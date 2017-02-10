package no.rutebanken.extime.converter;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import no.rutebanken.extime.config.NetexStaticDataSet;
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
import static no.rutebanken.extime.util.NetexObjectIdTypes.DESTINATION_DISPLAY;

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
    private NetexStaticDataSet netexStaticDataSet;

    @Autowired
    private NetexCommonDataSet netexCommonDataSet;

    @Autowired
    private ObjectFactory objectFactory;

    @Autowired
    private NetexObjectFactory netexObjectFactory;

    public JAXBElement<PublicationDeliveryStructure> convertToNetex(LineDataSet lineDataSet) throws Exception {
        if (netexObjectFactory != null) {
            netexObjectFactory.clearReferentials();
        }

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
        List<DestinationDisplay> destinationDisplaysForStops = createDestinationDisplaysForStopPoints(lineDataSet.getFlightRoutes());
        List<DestinationDisplay> destinationDisplaysForPatterns = createDestinationDisplaysForPatterns(journeyPatterns);
        List<DestinationDisplay> destinationDisplays = Lists.newArrayList(Iterables.concat(destinationDisplaysForPatterns, destinationDisplaysForStops));
        List<ServiceJourney> serviceJourneys = createServiceJourneys(line, lineDataSet.getRouteJourneys());

        Frames_RelStructure frames = objectFactory.createFrames_RelStructure();

        if (!isFrequentOperator) {
            logger.warn("Infrequent operator identified by id : {}", airlineIata);
            Operator operator = netexObjectFactory.createInfrequentAirlineOperatorElement(airlineIata, lineDataSet.getAirlineName(), operatorId);
            JAXBElement<ResourceFrame> resourceFrameElement = netexObjectFactory.createResourceFrameElement(operator);
            frames.getCommonFrame().add(resourceFrameElement);
        }

        JAXBElement<ServiceFrame> serviceFrame = netexObjectFactory.createServiceFrame(publicationTimestamp,
                lineDataSet.getAirlineName(), lineDataSet.getAirlineIata(), routePoints, routes, line, destinationDisplays, journeyPatterns);
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

    private List<RoutePoint> createRoutePoints(List<FlightRoute> flightRoutes) {
        Map<String, RoutePoint> routePointMap = netexCommonDataSet.getRoutePointMap();

        return flightRoutes.stream()
                .flatMap(flightRoute -> flightRoute.getRoutePointsInSequence().stream())
                .distinct()
                .sorted(Comparator.comparing(iata -> iata))
                .map(routePointMap::get)
                .collect(Collectors.toList());
    }

    private List<Route> createRoutes(Line line, List<FlightRoute> flightRoutes) {
        Map<String, RoutePoint> routePointMap = netexCommonDataSet.getRoutePointMap();
        List<Route> routes = Lists.newArrayList();

        if (!routeIdDesignationMap.isEmpty()) {
            routeIdDesignationMap.clear();
        }

        for (FlightRoute flightRoute : flightRoutes) {
            PointsOnRoute_RelStructure pointsOnRoute = objectFactory.createPointsOnRoute_RelStructure();
            List<String> routePointsInSequence = flightRoute.getRoutePointsInSequence();
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(routePointsInSequence.size());

            String objectId = Joiner.on(UNDERSCORE).skipNulls().join(localContext.get(AIRLINE_IATA), flightRoute.getRouteDesignation());
            String hashedObjectId = NetexObjectIdCreator.hashObjectId(objectId, 8);

            for (int i = 0; i < routePointsInSequence.size(); i++) {
                RoutePoint routePoint = routePointMap.get(routePointsInSequence.get(i));
                String pointOnRouteId = hashedObjectId + StringUtils.leftPad(idSequence[i], 2, "0");
                PointOnRoute pointOnRoute = netexObjectFactory.createPointOnRoute(pointOnRouteId, routePoint.getId());
                pointsOnRoute.getPointOnRoute().add(pointOnRoute);
            }

            Route route = netexObjectFactory.createRoute(line.getId(), hashedObjectId, flightRoute.getRouteName(), pointsOnRoute);
            routes.add(route);

            if (!routeIdDesignationMap.containsKey(route.getId())) {
                routeIdDesignationMap.put(route.getId(), flightRoute.getRouteDesignation());
            }
        }

        //routes.sort(Comparator.comparing(Route::getId));
        return routes;
    }

    private List<JourneyPattern> createJourneyPatterns(List<Route> routes) {
        Map<String, ScheduledStopPoint> stopPointMap = netexCommonDataSet.getStopPointMap();
        BiMap<String, String> airportHashes = netexCommonDataSet.getAirportHashes();
        List<JourneyPattern> journeyPatterns = Lists.newArrayList();

        if (!routeDesignationPatternMap.isEmpty()) {
            routeDesignationPatternMap.clear();
        }

        for (Route route : routes) {
            PointsInJourneyPattern_RelStructure pointsInJourneyPattern = objectFactory.createPointsInJourneyPattern_RelStructure();
            List<PointOnRoute> pointsOnRoute = route.getPointsInSequence().getPointOnRoute();

            for (int i = 0; i < pointsOnRoute.size(); i++) {
                PointOnRoute pointOnRoute = pointsOnRoute.get(i);
                String pointIdRef = pointOnRoute.getPointRef().getValue().getRef();
                String stopPointIdSuffix = NetexObjectIdCreator.getObjectIdSuffix(pointIdRef);
                ScheduledStopPoint scheduledStopPoint = stopPointMap.get(airportHashes.inverse().get(stopPointIdSuffix));
                String pointOnRouteIdSuffix = NetexObjectIdCreator.getObjectIdSuffix(pointOnRoute.getId());

                StopPointInJourneyPattern stopPointInJourneyPattern = netexObjectFactory.createStopPointInJourneyPattern(
                        pointOnRouteIdSuffix, BigInteger.valueOf(i + 1), scheduledStopPoint.getId());

                if (i == 0) {
                    stopPointInJourneyPattern.setForAlighting(false);
                }

                if (i == pointsOnRoute.size() - 1) {
                    stopPointInJourneyPattern.setForBoarding(false);
                }

                pointsInJourneyPattern.getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern().add(stopPointInJourneyPattern);
            }

            String objectId = Iterables.getLast(Splitter.on(COLON).trimResults().split(route.getId()));
            JourneyPattern journeyPattern = netexObjectFactory.createJourneyPattern(objectId, route.getId(), pointsInJourneyPattern);
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

    private List<DestinationDisplay> createDestinationDisplaysForStopPoints(List<FlightRoute> flightRoutes) {
        Map<String, NetexStaticDataSet.StopPlaceDataSet> stopPlaceDataSets = netexStaticDataSet.getStopPlaces();
        String objectIdPrefix = localContext.get(AIRLINE_IATA) + StringUtils.remove(localContext.get(LINE_DESIGNATION), DASH) + DASH;

        return flightRoutes.stream()
                .flatMap(flightRoute -> flightRoute.getRoutePointsInSequence().stream())
                .distinct()
                .sorted(Comparator.comparing(iata -> iata))
                .map(iata -> netexObjectFactory.createDestinationDisplay(objectIdPrefix + iata, stopPlaceDataSets.get(iata.toLowerCase()).getShortName(), true))
                .collect(Collectors.toList());
    }

    private List<DestinationDisplay> createDestinationDisplaysForPatterns(List<JourneyPattern> journeyPatterns) {
        Map<String, NetexStaticDataSet.StopPlaceDataSet> stopPlaceDataSets = netexStaticDataSet.getStopPlaces();
        BiMap<String, String> airportHashes = netexCommonDataSet.getAirportHashes();
        List<DestinationDisplay> destinationDisplays = Lists.newArrayList();

        for (JourneyPattern journeyPattern : journeyPatterns) {

            // get the id to set for the main destination display
            String routeIdRef = journeyPattern.getRouteRef().getRef();
            String objectId = Iterables.getLast(Splitter.on(COLON).trimResults().split(routeIdRef));
            DestinationDisplay patternDestinationDisplay = netexObjectFactory.createDestinationDisplay(objectId);

            // get the final destination from stop points sequence, to set as front text
            List<PointInLinkSequence_VersionedChildStructure> pointsInLinkSequence = journeyPattern.getPointsInSequence()
                    .getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern();
            PointInLinkSequence_VersionedChildStructure lastPointInSequence = pointsInLinkSequence.get(pointsInLinkSequence.size() - 1);
            StopPointInJourneyPattern finalDestinationPoint = (StopPointInJourneyPattern) lastPointInSequence;

            String finalDestinationPointId = finalDestinationPoint.getScheduledStopPointRef().getValue().getRef();
            String finalStopPointObjectId = Iterables.getLast(Splitter.on(COLON).trimResults().split(finalDestinationPointId));
            String frontTextValue = stopPlaceDataSets.get(airportHashes.inverse().get(finalStopPointObjectId).toLowerCase()).getShortName();
            patternDestinationDisplay.setFrontText(netexObjectFactory.createMultilingualString(frontTextValue));

            // check if needed to set vias
            if (pointsInLinkSequence.size() > 2) {
                Vias_RelStructure viasStruct = objectFactory.createVias_RelStructure();

                for (int i = 0; i < pointsInLinkSequence.size(); i++) {

                    if (i > 0 && i < pointsInLinkSequence.size() - 1) {
                        StopPointInJourneyPattern stopPointInJourneyPattern = (StopPointInJourneyPattern) pointsInLinkSequence.get(i);
                        String stopPointIdRef = stopPointInJourneyPattern.getScheduledStopPointRef().getValue().getRef();
                        String stopPointObjectId = Iterables.getLast(Splitter.on(COLON).trimResults().split(stopPointIdRef));
                        String inversedStopPointObjectId = airportHashes.inverse().get(stopPointObjectId);
                        String objectIdRef = localContext.get(AIRLINE_IATA) + StringUtils.remove(localContext.get(LINE_DESIGNATION), DASH) + DASH + inversedStopPointObjectId;

                        String destinationDisplayIdRef = Joiner.on(COLON).skipNulls().join(AVINOR_XMLNS, DESTINATION_DISPLAY, objectIdRef);
                        DestinationDisplay destinationDisplay = netexObjectFactory.getDestinationDisplay(destinationDisplayIdRef);

                        if (destinationDisplay != null) {
                            DestinationDisplayRefStructure destinationDisplayRefStruct = netexObjectFactory.createDestinationDisplayRefStructure(destinationDisplay.getId());
                            Via_VersionedChildStructure viaChildStruct = objectFactory.createVia_VersionedChildStructure();
                            viaChildStruct.setDestinationDisplayRef(destinationDisplayRefStruct);
                            viasStruct.getVia().add(viaChildStruct);
                        }
                    }
                }
                patternDestinationDisplay.setVias(viasStruct);
            }
            destinationDisplays.add(patternDestinationDisplay);
            DestinationDisplayRefStructure destinationDisplayRefStruct = netexObjectFactory.createDestinationDisplayRefStructure(patternDestinationDisplay.getId());
            journeyPattern.setDestinationDisplayRef(destinationDisplayRefStruct);
        }
        return destinationDisplays;
    }

    private List<ServiceJourney> createServiceJourneys(Line line, Map<String, Map<String, List<ScheduledFlight>>> routeJourneys) {
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
        DayTypeRefs_RelStructure dayTypeStructure = objectFactory.createDayTypeRefs_RelStructure();
        journeyFlights.sort(Comparator.comparing(ScheduledFlight::getDateOfOperation));

        for (int i = 0; i < journeyFlights.size(); i++) {
            LocalDate dateOfOperation = journeyFlights.get(i).getDateOfOperation();

            String dayTypeIdSuffix = localContext.get(AIRLINE_IATA) + StringUtils.remove(localContext.get(LINE_DESIGNATION), DASH)
                    + DASH + dateOfOperation.format(DateTimeFormatter.ofPattern("EEE_dd"));

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
                    + DASH + dateOfOperation.format(DateTimeFormatter.ofPattern("EEE_dd"));

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
