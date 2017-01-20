package no.rutebanken.extime.converter;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import no.rutebanken.extime.model.*;
import no.rutebanken.extime.util.NetexObjectFactory;
import no.rutebanken.extime.util.NetexObjectIdCreator;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.extime.Constants.*;

@Component(value = "lineDataToNetexConverter")
public class LineDataToNetexConverter {
    private static final Logger logger = LoggerFactory.getLogger(LineDataToNetexConverter.class);

    private static final String AIRLINE_IATA = "airline_iata";
    private static final String LINE_DESIGNATION = "line_designation";

    private Map<String, String> localContext = new HashMap<>();
    private Map<String, String> routeIdDesignationMap = new HashMap<>();
    private Map<String, JourneyPattern> routeDesignationPatternMap = new HashMap<>();
    private Map<String, DayType> dayTypes = new HashMap<>();
    private Map<String, DayTypeAssignment> dayTypeAssignments = new HashMap<>();

    @Autowired
    private NetexCommonDataSet netexCommonDataSet;

    @Autowired
    private ObjectFactory objectFactory;

    @Autowired
    private NetexObjectFactory netexObjectFactory;

    public JAXBElement<PublicationDeliveryStructure> convertToNetex(LineDataSet lineDataSet) throws Exception {
        localContext.put(AIRLINE_IATA, lineDataSet.getAirlineIata());
        localContext.put(LINE_DESIGNATION, lineDataSet.getLineDesignation());

        OffsetDateTime publicationTimestamp = OffsetDateTime.ofInstant(Instant.now(), ZoneId.of(DEFAULT_ZONE_ID));
        AvailabilityPeriod availabilityPeriod = lineDataSet.getAvailabilityPeriod();
        String airlineIata = lineDataSet.getAirlineIata();

        String operatorId = NetexObjectIdCreator.createOperatorId(AVINOR_XMLNS, airlineIata);
        boolean isFrequentOperator = isCommonDesignator(airlineIata);

        Line line = netexObjectFactory.createLine(lineDataSet.getAirlineIata(), lineDataSet.getLineDesignation(), lineDataSet.getLineName());
        line.setOperatorRef(isFrequentOperator ? netexObjectFactory.createOperatorRefStructure(operatorId, Boolean.FALSE) :
                netexObjectFactory.createOperatorRefStructure(operatorId, Boolean.TRUE));

        List<RoutePoint> routePoints = createRoutePoints(lineDataSet.getFlightRoutes());
        List<Route> routes = createRoutes(line, lineDataSet.getFlightRoutes());
        List<RouteRefStructure> routeRefStructures = netexObjectFactory.createRouteRefStructures(routes);
        RouteRefs_RelStructure routeRefStruct = objectFactory.createRouteRefs_RelStructure().withRouteRef(routeRefStructures);
        line.setRoutes(routeRefStruct);

        List<JourneyPattern> journeyPatterns = createJourneyPatterns(routes);
        List<ServiceJourney> serviceJourneys = createServiceJourneys(line, lineDataSet.getRouteJourneys());

        Frames_RelStructure frames = objectFactory.createFrames_RelStructure();

        if (!isFrequentOperator) {
            logger.warn("Infrequent operator identified by id : {}", airlineIata);
            Operator operator = netexObjectFactory.createInfrequentAirlineOperatorElement(airlineIata, lineDataSet.getAirlineName(), operatorId);
            JAXBElement<ResourceFrame> resourceFrameElement = netexObjectFactory.createResourceFrameElement(operator);
            frames.getCommonFrame().add(resourceFrameElement);
        }

        JAXBElement<ServiceFrame> serviceFrame = netexObjectFactory.createServiceFrame(publicationTimestamp,
                lineDataSet.getAirlineName(), lineDataSet.getAirlineIata(), routePoints, routes, line, journeyPatterns);
        frames.getCommonFrame().add(serviceFrame);

        JAXBElement<TimetableFrame> timetableFrame = netexObjectFactory.createTimetableFrame(serviceJourneys);
        frames.getCommonFrame().add(timetableFrame);

        JAXBElement<ServiceCalendarFrame> serviceCalendarFrame = netexObjectFactory.createServiceCalendarFrame(dayTypes, dayTypeAssignments);
        frames.getCommonFrame().add(serviceCalendarFrame);

        JAXBElement<CompositeFrame> compositeFrame = netexObjectFactory.createCompositeFrame(publicationTimestamp,
                availabilityPeriod, lineDataSet.getAirlineIata(), lineDataSet.getLineDesignation(), frames);

        PublicationDeliveryStructure publicationDeliveryStructure = netexObjectFactory.createPublicationDeliveryStructure(
                publicationTimestamp, compositeFrame, lineDataSet.getLineName());

        return objectFactory.createPublicationDelivery(publicationDeliveryStructure);
    }

    public List<RoutePoint> createRoutePoints(List<FlightRoute> flightRoutes) {
        logger.debug("Creating route points");
        Map<String, RoutePoint> routePointMap = netexCommonDataSet.getRoutePointMap();

        return flightRoutes.stream()
                .flatMap(flightRoute -> flightRoute.getRoutePointsInSequence().stream())
                .distinct()
                .sorted(Comparator.comparing(iata -> iata))
                .map(routePointMap::get)
                .collect(Collectors.toList());
    }

    private List<Route> createRoutes(Line line, List<FlightRoute> flightRoutes) {
        logger.debug("Creating routes");
        Map<String, RoutePoint> routePointMap = netexCommonDataSet.getRoutePointMap();
        List<Route> routes = Lists.newArrayList();

        if (!routeIdDesignationMap.isEmpty()) {
            routeIdDesignationMap.clear();
        }

        for (FlightRoute flightRoute : flightRoutes) {
            PointsOnRoute_RelStructure pointsOnRoute = objectFactory.createPointsOnRoute_RelStructure();
            List<String> routePointsInSequence = flightRoute.getRoutePointsInSequence();
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, routePointsInSequence.size());

            for (int i = 0; i < routePointsInSequence.size(); i++) {
                RoutePoint routePoint = routePointMap.get(routePointsInSequence.get(i));
                PointOnRoute pointOnRoute = netexObjectFactory.createPointOnRoute(idSequence[i], routePoint.getId());
                pointsOnRoute.getPointOnRoute().add(pointOnRoute);
            }

            Route route = netexObjectFactory.createRoute(line.getId(), flightRoute.getRouteDesignation(), flightRoute.getRouteName(), pointsOnRoute);
            routes.add(route);

            if (!routeIdDesignationMap.containsKey(route.getId())) {
                routeIdDesignationMap.put(route.getId(), flightRoute.getRouteDesignation());
            }
        }

        routes.sort(Comparator.comparing(Route::getId));
        return routes;
    }

    public List<JourneyPattern> createJourneyPatterns(List<Route> routes) {
        logger.debug("Creating journey patterns");
        Map<String, ScheduledStopPoint> stopPointMap = netexCommonDataSet.getStopPointMap();
        List<JourneyPattern> journeyPatterns = Lists.newArrayList();

        if (!routeDesignationPatternMap.isEmpty()) {
            routeDesignationPatternMap.clear();
        }

        for (Route route : routes) {
            PointsInJourneyPattern_RelStructure pointsInJourneyPattern = objectFactory.createPointsInJourneyPattern_RelStructure();
            List<PointOnRoute> pointsOnRoute = route.getPointsInSequence().getPointOnRoute();
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, pointsOnRoute.size());

            for (int i = 0; i < pointsOnRoute.size(); i++) {
                String pointIdRef = pointsOnRoute.get(i).getPointRef().getValue().getRef();
                String airportIata = Iterables.getLast(Splitter.on(COLON).trimResults().split(pointIdRef));
                ScheduledStopPoint scheduledStopPoint = stopPointMap.get(airportIata);

                StopPointInJourneyPattern stopPointInJourneyPattern = netexObjectFactory.createStopPointInJourneyPattern(
                        idSequence[i], BigInteger.valueOf(i + 1), scheduledStopPoint.getId());

                if (i == 0) {
                    stopPointInJourneyPattern.setForAlighting(false);
                }

                if (i == pointsOnRoute.size() - 1) {
                    stopPointInJourneyPattern.setForBoarding(false);
                }

                pointsInJourneyPattern.getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern().add(stopPointInJourneyPattern);
            }

            int journeyPatternObjectId = NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE);

            JourneyPattern journeyPattern = netexObjectFactory.createJourneyPattern(
                    String.valueOf(journeyPatternObjectId), route.getId(), pointsInJourneyPattern);
            journeyPatterns.add(journeyPattern);

            if (routeIdDesignationMap.containsKey(route.getId())) {
                String routeDesignation = routeIdDesignationMap.get(route.getId());
                if (!routeDesignationPatternMap.containsKey(routeDesignation)) {
                    routeDesignationPatternMap.put(routeDesignation, journeyPattern);
                }
            }
        }

        journeyPatterns.sort(Comparator.comparing(JourneyPattern::getId));
        return journeyPatterns;
    }

    public List<ServiceJourney> createServiceJourneys(Line line, Map<String, Map<String, List<ScheduledFlight>>> routeJourneys) {
        logger.debug("Creating service journeys");
        List<ServiceJourney> serviceJourneyList = new ArrayList<>();

        if (!dayTypes.isEmpty()) {
            dayTypes.clear();
        }
        if (!dayTypeAssignments.isEmpty()) {
            dayTypeAssignments.clear();
        }

        for (Map.Entry<String, Map<String, List<ScheduledFlight>>> entry : routeJourneys.entrySet()) {
            String routeDesignation = entry.getKey();
            JourneyPattern journeyPattern = null;

            if (routeDesignationPatternMap.containsKey(routeDesignation)) {
                journeyPattern = routeDesignationPatternMap.get(routeDesignation);
            }

            Map<String, List<ScheduledFlight>> flightsById = entry.getValue();

            for (Map.Entry<String, List<ScheduledFlight>> subEntry : flightsById.entrySet()) {
                String flightId = subEntry.getKey();
                List<ScheduledFlight> flights = subEntry.getValue();

                Map<String, List<ScheduledFlight>> flightsByStopTimes = flights.stream()
                        .collect(Collectors.groupingBy(ScheduledFlight::getStopTimesPattern));

                List<ServiceJourney> serviceJourneys = createServiceJourneys(line, flightId, journeyPattern, flightsByStopTimes);
                serviceJourneyList.addAll(serviceJourneys);
            }
        }

        return serviceJourneyList;
    }

    private List<ServiceJourney> createServiceJourneys(Line line, String flightId, JourneyPattern journeyPattern, Map<String, List<ScheduledFlight>> flightsByStopTimes) {
        List<ServiceJourney> serviceJourneys = new ArrayList<>();
        String journeyPatternId = journeyPattern.getId();

        List<PointInLinkSequence_VersionedChildStructure> pointsInLinkSequence = journeyPattern.getPointsInSequence()
                .getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern();

        for (Map.Entry<String, List<ScheduledFlight>> entry : flightsByStopTimes.entrySet()) {
            List<ScheduledFlight> journeyFlights = entry.getValue();
            TimetabledPassingTimes_RelStructure passingTimesRelStruct = aggregateJourneyPassingTimes(journeyFlights, pointsInLinkSequence);
            DayTypeRefs_RelStructure dayTypeRefsStruct = collectDayTypesAndAssignments(journeyFlights);
            ServiceJourney serviceJourney = netexObjectFactory.createServiceJourney(line.getId(), flightId, dayTypeRefsStruct, journeyPatternId, passingTimesRelStruct);
            serviceJourneys.add(serviceJourney);
        }

        return serviceJourneys;
    }

    private TimetabledPassingTimes_RelStructure aggregateJourneyPassingTimes(List<ScheduledFlight> journeyFlights, List<PointInLinkSequence_VersionedChildStructure> pointsInLinkSequence) {
        logger.debug("Aggregating passing times");
        ScheduledFlight guidingFlight = journeyFlights.get(0);
        TimetabledPassingTimes_RelStructure passingTimesRelStructure = objectFactory.createTimetabledPassingTimes_RelStructure();

        if (guidingFlight.hasStopovers()) {
            List<ScheduledStopover> scheduledStopovers = guidingFlight.getScheduledStopovers();
            Iterator<ScheduledStopover> stopoverIterator = scheduledStopovers.iterator();
            Iterator<PointInLinkSequence_VersionedChildStructure> pointInLinkSequenceIterator = pointsInLinkSequence.iterator();

            while (stopoverIterator.hasNext() && pointInLinkSequenceIterator.hasNext()) {
                ScheduledStopover scheduledStopover = stopoverIterator.next();
                PointInLinkSequence_VersionedChildStructure pointInLinkSequence = pointInLinkSequenceIterator.next();
                TimetabledPassingTime passingTime = netexObjectFactory.createTimetabledPassingTime(pointInLinkSequence.getId());

                if (scheduledStopover.getArrivalTime() != null) {
                    passingTime.setArrivalTime(scheduledStopover.getArrivalTime());
                }
                if (scheduledStopover.getDepartureTime() != null) {
                    passingTime.setDepartureTime(scheduledStopover.getDepartureTime());
                }

                passingTimesRelStructure.withTimetabledPassingTime(passingTime);
            }
        } else {
            TimetabledPassingTime departurePassingTime = netexObjectFactory.createTimetabledPassingTime(pointsInLinkSequence.get(0).getId());
            departurePassingTime.setDepartureTime(guidingFlight.getTimeOfDeparture());
            passingTimesRelStructure.withTimetabledPassingTime(departurePassingTime);

            TimetabledPassingTime arrivalPassingTime = netexObjectFactory.createTimetabledPassingTime(pointsInLinkSequence.get(1).getId());
            arrivalPassingTime.setArrivalTime(guidingFlight.getTimeOfArrival());
            passingTimesRelStructure.withTimetabledPassingTime(arrivalPassingTime);
        }

        return passingTimesRelStructure;
    }

    private DayTypeRefs_RelStructure collectDayTypesAndAssignments(List<ScheduledFlight> journeyFlights) {
        logger.debug("Collecting day types and assignments");
        DayTypeRefs_RelStructure dayTypeStructure = objectFactory.createDayTypeRefs_RelStructure();
        journeyFlights.sort(Comparator.comparing(ScheduledFlight::getDateOfOperation));

        for (int i = 0; i < journeyFlights.size(); i++) {
            LocalDate dateOfOperation = journeyFlights.get(i).getDateOfOperation();

            String dayTypeIdSuffix = localContext.get(AIRLINE_IATA) + StringUtils.remove(localContext.get(LINE_DESIGNATION), DASH)
                    + COLON + dateOfOperation.format(DateTimeFormatter.ofPattern("EEE_dd"));
            //String dayTypeIdSuffix = dateOfOperation.format(DateTimeFormatter.ofPattern("EEE_dd"));

            String dayTypeId = NetexObjectIdCreator.createDayTypeId(AVINOR_XMLNS, dayTypeIdSuffix);

            DayType dayType;
            if (!dayTypes.containsKey(dayTypeId)) {
                dayType = netexObjectFactory.createDayType(dayTypeId);
                dayTypes.put(dayTypeId, dayType);
            }

            DayTypeRefStructure dayTypeRefStruct = netexObjectFactory.createDayTypeRefStructure(dayTypeId);
            JAXBElement<DayTypeRefStructure> dayTypeRefStructElement = objectFactory.createDayTypeRef(dayTypeRefStruct);
            dayTypeStructure.getDayTypeRef().add(dayTypeRefStructElement);

            String assignmentIdSuffix = localContext.get(AIRLINE_IATA) + StringUtils.remove(localContext.get(LINE_DESIGNATION), DASH)
                    + COLON + dateOfOperation.format(DateTimeFormatter.ofPattern("EEE_dd"));
            //String assignmentIdSuffix = dateOfOperation.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            DayTypeAssignment dayTypeAssignment;
            if (!dayTypeAssignments.containsKey(dayTypeId)) {
                dayTypeAssignment = netexObjectFactory.createDayTypeAssignment(assignmentIdSuffix, i + 1, dateOfOperation, dayTypeId);
                dayTypeAssignments.put(dayTypeAssignment.getId(), dayTypeAssignment);
            }
        }

        return dayTypeStructure;
    }

    private boolean isCommonDesignator(String airlineIata) {
        if (EnumUtils.isValidEnum(AirlineDesignator.class, airlineIata.toUpperCase())) {
            AirlineDesignator designator = AirlineDesignator.valueOf(airlineIata.toUpperCase());
            return AirlineDesignator.commonDesignators.contains(designator);
        }
        return false;
    }

}
