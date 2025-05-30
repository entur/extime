package no.rutebanken.extime.converter;


import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import no.rutebanken.extime.config.NetexStaticDataSet;
import no.rutebanken.extime.model.AvailabilityPeriod;
import no.rutebanken.extime.model.FlightRoute;
import no.rutebanken.extime.model.LineDataSet;
import no.rutebanken.extime.model.ScheduledFlight;
import no.rutebanken.extime.model.ScheduledStopover;
import no.rutebanken.extime.util.ExtimeException;
import no.rutebanken.extime.util.NetexObjectFactory;
import no.rutebanken.extime.util.NetexObjectIdCreator;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.netex.model.CompositeFrame;
import org.rutebanken.netex.model.DayType;
import org.rutebanken.netex.model.DayTypeAssignment;
import org.rutebanken.netex.model.DayTypeRefStructure;
import org.rutebanken.netex.model.DayTypeRefs_RelStructure;
import org.rutebanken.netex.model.DestinationDisplay;
import org.rutebanken.netex.model.DestinationDisplayRefStructure;
import org.rutebanken.netex.model.Frames_RelStructure;
import org.rutebanken.netex.model.JourneyPattern;
import org.rutebanken.netex.model.Line;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.OperatingPeriod;
import org.rutebanken.netex.model.PointInLinkSequence_VersionedChildStructure;
import org.rutebanken.netex.model.PointOnRoute;
import org.rutebanken.netex.model.PointsInJourneyPattern_RelStructure;
import org.rutebanken.netex.model.PointsOnRoute_RelStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.Route;
import org.rutebanken.netex.model.RoutePoint;
import org.rutebanken.netex.model.RouteRefStructure;
import org.rutebanken.netex.model.RouteRefs_RelStructure;
import org.rutebanken.netex.model.ScheduledStopPoint;
import org.rutebanken.netex.model.ServiceCalendarFrame;
import org.rutebanken.netex.model.ServiceFrame;
import org.rutebanken.netex.model.ServiceJourney;
import org.rutebanken.netex.model.StopPointInJourneyPattern;
import org.rutebanken.netex.model.TimetableFrame;
import org.rutebanken.netex.model.TimetabledPassingTime;
import org.rutebanken.netex.model.TimetabledPassingTimes_RelStructure;
import org.rutebanken.netex.model.Via_VersionedChildStructure;
import org.rutebanken.netex.model.Vias_RelStructure;
import org.springframework.stereotype.Component;

import jakarta.xml.bind.JAXBElement;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static no.rutebanken.extime.Constants.AVINOR_XMLNS;
import static no.rutebanken.extime.Constants.COLON;
import static no.rutebanken.extime.Constants.DASH;
import static no.rutebanken.extime.Constants.DAY_TYPE_PATTERN;
import static no.rutebanken.extime.Constants.UNDERSCORE;
import static no.rutebanken.extime.util.NetexObjectIdCreator.hashObjectId;
import static no.rutebanken.extime.util.NetexObjectIdTypes.DESTINATION_DISPLAY;

@Component(value = "lineDataToNetexConverter")
public class LineDataToNetexConverter {

    private static final String AIRLINE_IATA = "airline_iata";
    private static final String LINE_DESIGNATION = "line_designation";

    private final Map<String, String> localContext = new HashMap<>();
    private final Map<String, String> routeIdDesignationMap = new HashMap<>();
    private final Map<String, JourneyPattern> routeDesignationPatternMap = new HashMap<>();
    private final Map<String, DayType> dayTypes = new HashMap<>();
    private final Map<String, DayTypeAssignment> dayTypeAssignments = new HashMap<>();
    private final Map<String, OperatingPeriod> operatingPeriods = new HashMap<>();

    private final NetexStaticDataSet netexStaticDataSet;

    private final NetexCommonDataSet netexCommonDataSet;

    private final ObjectFactory objectFactory;

    private final NetexObjectFactory netexObjectFactory;

    public LineDataToNetexConverter(NetexStaticDataSet netexStaticDataSet, NetexCommonDataSet netexCommonDataSet, ObjectFactory objectFactory, NetexObjectFactory netexObjectFactory) {
        this.netexStaticDataSet = netexStaticDataSet;
        this.netexCommonDataSet = netexCommonDataSet;
        this.objectFactory = objectFactory;
        this.netexObjectFactory = netexObjectFactory;
    }


    public JAXBElement<PublicationDeliveryStructure> convertToNetex(LineDataSet lineDataSet) {
        try {

            netexObjectFactory.clearReferentials();

            localContext.put(AIRLINE_IATA, lineDataSet.getAirlineIata());
            localContext.put(LINE_DESIGNATION, lineDataSet.getLineDesignation());

            Instant publicationTimestamp = Instant.now();
            AvailabilityPeriod availabilityPeriod = lineDataSet.getAvailabilityPeriod();
            String airlineIata = lineDataSet.getAirlineIata();

            String operatorId = NetexObjectIdCreator.createOperatorId(AVINOR_XMLNS, airlineIata);

            Line line = netexObjectFactory.createLine(lineDataSet.getAirlineIata(), lineDataSet.getLineDesignation(), lineDataSet.getLineName());
            line.setOperatorRef(netexObjectFactory.createOperatorRefStructure(operatorId, Boolean.FALSE));

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

            JAXBElement<ServiceFrame> serviceFrame = netexObjectFactory.createServiceFrame(
                    routes, line, destinationDisplays, journeyPatterns);
            frames.getCommonFrame().add(serviceFrame);

            JAXBElement<TimetableFrame> timetableFrame = netexObjectFactory.createTimetableFrame(serviceJourneys);
            frames.getCommonFrame().add(timetableFrame);

            JAXBElement<ServiceCalendarFrame> serviceCalendarFrame = netexObjectFactory.createServiceCalendarFrame(dayTypes, dayTypeAssignments, operatingPeriods);
            frames.getCommonFrame().add(serviceCalendarFrame);

            JAXBElement<CompositeFrame> compositeFrame = netexObjectFactory.createCompositeFrame(publicationTimestamp,
                    availabilityPeriod, lineDataSet.getAirlineIata(), lineDataSet.getLineDesignation(), frames);

            PublicationDeliveryStructure publicationDeliveryStructure = netexObjectFactory.createPublicationDeliveryStructure(
                    publicationTimestamp, compositeFrame, lineDataSet.getLineName());

            return objectFactory.createPublicationDelivery(publicationDeliveryStructure);
        } finally {
            // Empty all session data
            clearAll();
        }
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

            String objectId = Joiner.on(UNDERSCORE).skipNulls().join(localContext.get(AIRLINE_IATA), flightRoute.routeDesignation());
            String hashedObjectId = hashObjectId(objectId, 10);

            for (int i = 0; i < routePointsInSequence.size(); i++) {
                RoutePoint routePoint = routePointMap.get(routePointsInSequence.get(i));
                String pointOnRouteId = hashedObjectId + StringUtils.leftPad(idSequence[i], 2, "0");
                PointOnRoute pointOnRoute = netexObjectFactory.createPointOnRoute(pointOnRouteId, routePoint.getId(), i + 1);
                pointsOnRoute.getPointOnRoute().add(pointOnRoute);
            }

            Route route = netexObjectFactory.createRoute(line.getId(), hashedObjectId, flightRoute.routeName(), pointsOnRoute);
            routes.add(route);

            if (!routeIdDesignationMap.containsKey(route.getId())) {
                routeIdDesignationMap.put(route.getId(), flightRoute.routeDesignation());
            }
        }

        return routes;
    }

    private List<JourneyPattern> createJourneyPatterns(List<Route> routes) {
        Map<String, ScheduledStopPoint> stopPointMap = netexCommonDataSet.getStopPointMap();
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
                ScheduledStopPoint scheduledStopPoint = stopPointMap.get(stopPointIdSuffix);
                String pointOnRouteIdSuffix = NetexObjectIdCreator.getObjectIdSuffix(pointOnRoute.getId());

                StopPointInJourneyPattern stopPointInJourneyPattern = netexObjectFactory.createStopPointInJourneyPattern(
                        pointOnRouteIdSuffix, BigInteger.valueOf((long)i + 1), scheduledStopPoint.getId());

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
                .toList();
    }

    private List<DestinationDisplay> createDestinationDisplaysForPatterns(List<JourneyPattern> journeyPatterns) {
        Map<String, NetexStaticDataSet.StopPlaceDataSet> stopPlaceDataSets = netexStaticDataSet.getStopPlaces();
       List<DestinationDisplay> destinationDisplays = Lists.newArrayList();

        for (JourneyPattern journeyPattern : journeyPatterns) {

            // get the id to set for the main destination display
            String routeIdRef = journeyPattern.getRouteRef().getRef();
            String objectId = Iterables.getLast(Splitter.on(COLON).trimResults().split(routeIdRef));
            DestinationDisplay patternDestinationDisplay = netexObjectFactory.createDestinationDisplay(objectId);
            DestinationDisplayRefStructure destinationDisplayRefStruct = netexObjectFactory.createDestinationDisplayRefStructure(patternDestinationDisplay.getId());

            // get the final destination from stop points sequence, to set as front text
            List<PointInLinkSequence_VersionedChildStructure> pointsInLinkSequence = journeyPattern.getPointsInSequence()
                    .getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern();
            PointInLinkSequence_VersionedChildStructure lastPointInSequence = pointsInLinkSequence.getLast();
            StopPointInJourneyPattern finalDestinationPoint = (StopPointInJourneyPattern) lastPointInSequence;

            String finalDestinationPointId = finalDestinationPoint.getScheduledStopPointRef().getValue().getRef();
            String finalStopPointObjectId = Iterables.getLast(Splitter.on(COLON).trimResults().split(finalDestinationPointId));
            String frontTextValue = stopPlaceDataSets.get(finalStopPointObjectId.toLowerCase()).getShortName();
            patternDestinationDisplay.setFrontText(netexObjectFactory.createMultilingualString(frontTextValue));
            patternDestinationDisplay.setName(patternDestinationDisplay.getFrontText());

            StopPointInJourneyPattern firstStopPointInJourneyPattern = (StopPointInJourneyPattern) pointsInLinkSequence.getFirst();
            firstStopPointInJourneyPattern.setDestinationDisplayRef(destinationDisplayRefStruct);          		
            
            List<String> viaTexts = new ArrayList<>();
            
            // check if needed to set vias
            if (pointsInLinkSequence.size() > 2) {
                Vias_RelStructure viasStruct = objectFactory.createVias_RelStructure();

                for (int i = 0; i < pointsInLinkSequence.size(); i++) {
                    if (i > 0 && i < pointsInLinkSequence.size() - 1) {
                        StopPointInJourneyPattern stopPointInJourneyPattern = (StopPointInJourneyPattern) pointsInLinkSequence.get(i);
                        String stopPointIdRef = stopPointInJourneyPattern.getScheduledStopPointRef().getValue().getRef();
                        String stopPointObjectId = Iterables.getLast(Splitter.on(COLON).trimResults().split(stopPointIdRef));
                        String objectIdRef = localContext.get(AIRLINE_IATA) + StringUtils.remove(localContext.get(LINE_DESIGNATION), DASH) + DASH + stopPointObjectId;

                        String destinationDisplayIdRef = Joiner.on(COLON).skipNulls().join(AVINOR_XMLNS, DESTINATION_DISPLAY, objectIdRef);
                        DestinationDisplay destinationDisplay = netexObjectFactory.getDestinationDisplay(destinationDisplayIdRef);

                        if (destinationDisplay != null) {
                            DestinationDisplayRefStructure viaRefStruct = netexObjectFactory.createDestinationDisplayRefStructure(destinationDisplay.getId());
                            Via_VersionedChildStructure viaChildStruct = objectFactory.createVia_VersionedChildStructure();
                            viaChildStruct.setDestinationDisplayRef(viaRefStruct);
                            viasStruct.getVia().add(viaChildStruct);
                            String frontTextValueVia = stopPlaceDataSets.get(stopPointObjectId.toLowerCase()).getShortName();
                            viaTexts.add(frontTextValueVia);
                        }
                    }
                }
                patternDestinationDisplay.setVias(viasStruct);
            }
            
            if(!viaTexts.isEmpty()) {
            	String newName = patternDestinationDisplay.getName().getValue()+" (via "+StringUtils.join(viaTexts," ")+")";
            	patternDestinationDisplay.setName(netexObjectFactory.createMultilingualString(newName));
            }
            
            destinationDisplays.add(patternDestinationDisplay);
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
        if (!operatingPeriods.isEmpty()) {
            operatingPeriods.clear();
        }

        for (Map.Entry<String, Map<String, List<ScheduledFlight>>> entry : routeJourneys.entrySet()) {
            String routeDesignation = entry.getKey();
            JourneyPattern journeyPattern;

            if (routeDesignationPatternMap.containsKey(routeDesignation)) {
                journeyPattern = routeDesignationPatternMap.get(routeDesignation);
            } else {
                throw new ExtimeException("Route not found: " + routeDesignation);
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

        String[] idSequence = NetexObjectIdCreator.generateIdSequence(flightsByStopTimes.size());

        int index = 0;
        for (Map.Entry<String, List<ScheduledFlight>> entry : flightsByStopTimes.entrySet()) {
            List<ScheduledFlight> journeyFlights = entry.getValue();

            TimetabledPassingTimes_RelStructure passingTimesRelStruct = aggregateJourneyPassingTimes(journeyFlights, pointsInLinkSequence);

            DayTypeRefs_RelStructure dayTypeRefsStruct;
            dayTypeRefsStruct = collectDayTypesAndAssignments(journeyFlights);

            String journeyIdSequence = StringUtils.leftPad(idSequence[index++], 2, "0");
            String objectId = Joiner.on(DASH).skipNulls().join(flightId, journeyIdSequence, NetexObjectIdCreator.getObjectIdSuffix(journeyPatternId));
            ServiceJourney serviceJourney = netexObjectFactory.createServiceJourney(objectId, line.getId(), flightId, dayTypeRefsStruct, journeyPatternId, passingTimesRelStruct,journeyFlights.getFirst().getArrivalAirportName());
            serviceJourneys.add(serviceJourney);
        }

        return serviceJourneys;
    }

    private TimetabledPassingTimes_RelStructure aggregateJourneyPassingTimes(List<ScheduledFlight> journeyFlights, List<PointInLinkSequence_VersionedChildStructure> pointsInLinkSequence) {
        ScheduledFlight guidingFlight = journeyFlights.getFirst();
        TimetabledPassingTimes_RelStructure passingTimesRelStructure = objectFactory.createTimetabledPassingTimes_RelStructure();

        if (guidingFlight.hasStopovers()) {
            List<ScheduledStopover> scheduledStopovers = guidingFlight.getScheduledStopovers();
            Iterator<ScheduledStopover> stopoverIterator = scheduledStopovers.iterator();
            Iterator<PointInLinkSequence_VersionedChildStructure> pointInLinkSequenceIterator = pointsInLinkSequence.iterator();

            LocalTime previousArrivalOrDepartureTime = null;
            BigInteger dayOffset = BigInteger.ZERO;
            
            while (stopoverIterator.hasNext() && pointInLinkSequenceIterator.hasNext()) {
                ScheduledStopover scheduledStopover = stopoverIterator.next();
                PointInLinkSequence_VersionedChildStructure pointInLinkSequence = pointInLinkSequenceIterator.next();
                TimetabledPassingTime passingTime = netexObjectFactory.createTimetabledPassingTime(pointInLinkSequence.getId());

                if (scheduledStopover.getArrivalTime() != null) {
                    passingTime.setArrivalTime(scheduledStopover.getArrivalTime());
                    if(previousArrivalOrDepartureTime != null && scheduledStopover.getArrivalTime().isBefore(previousArrivalOrDepartureTime)) {
                        dayOffset = dayOffset.add(BigInteger.ONE);
                    }
                    if(!dayOffset.equals(BigInteger.ZERO)) {
                    	passingTime.setArrivalDayOffset(dayOffset);
                    }
                    previousArrivalOrDepartureTime = scheduledStopover.getArrivalTime();
                }

                if (scheduledStopover.getDepartureTime() != null) {
                    passingTime.setDepartureTime(scheduledStopover.getDepartureTime());
                    if(previousArrivalOrDepartureTime != null && scheduledStopover.getDepartureTime().isBefore(previousArrivalOrDepartureTime)) {
                        dayOffset = dayOffset.add(BigInteger.ONE);
                    }
                    if(!dayOffset.equals(BigInteger.ZERO)) {
                    	passingTime.setDepartureDayOffset(dayOffset);
                    }
                    previousArrivalOrDepartureTime = scheduledStopover.getDepartureTime();
                }

                passingTimesRelStructure.withTimetabledPassingTime(passingTime);
            }
        } else {
            TimetabledPassingTime departurePassingTime = netexObjectFactory.createTimetabledPassingTime(pointsInLinkSequence.getFirst().getId());
            departurePassingTime.setDepartureTime(guidingFlight.getTimeOfDeparture());
            passingTimesRelStructure.withTimetabledPassingTime(departurePassingTime);
            
            TimetabledPassingTime arrivalPassingTime = netexObjectFactory.createTimetabledPassingTime(pointsInLinkSequence.get(1).getId());
            arrivalPassingTime.setArrivalTime(guidingFlight.getTimeOfArrival());
            passingTimesRelStructure.withTimetabledPassingTime(arrivalPassingTime);
            if(guidingFlight.getTimeOfArrival().isBefore(guidingFlight.getTimeOfDeparture())) {
            	arrivalPassingTime.setArrivalDayOffset(BigInteger.ONE);
            }
        }
        
     

        return passingTimesRelStructure;
    }

    private DayTypeRefs_RelStructure collectDayTypesAndAssignments(List<ScheduledFlight> journeyFlights) {
        DayTypeRefs_RelStructure dayTypeStructure = objectFactory.createDayTypeRefs_RelStructure();
        List<LocalDate> datesOfOperation = journeyFlights.stream().map(ScheduledFlight::getDateOfOperation).sorted().toList();
        collectDayTypesAndAssignments(dayTypeStructure, datesOfOperation);
        return dayTypeStructure;
    }

    private void collectDayTypesAndAssignments(DayTypeRefs_RelStructure dayTypeStructure, List<LocalDate> datesOfOperation) {
        for (int i = 0; i < datesOfOperation.size(); i++) {
            LocalDate dateOfOperation = datesOfOperation.get(i);

            String dayTypeIdLinePart = getIdLinePart();
            String formattedDateOfOperation = dateOfOperation.format(DateTimeFormatter.ofPattern(DAY_TYPE_PATTERN));
            String dayTypeIdSuffix = Joiner.on(DASH).skipNulls().join(dayTypeIdLinePart, formattedDateOfOperation);
            String dayTypeId = NetexObjectIdCreator.createDayTypeId(AVINOR_XMLNS, dayTypeIdSuffix);

            DayType dayType;
            if (!dayTypes.containsKey(dayTypeId)) {
                dayType = netexObjectFactory.createDayType(dayTypeId);
                dayTypes.put(dayTypeId, dayType);
            }

            DayTypeRefStructure dayTypeRefStruct = netexObjectFactory.createDayTypeRefStructure(dayTypeId);
            JAXBElement<DayTypeRefStructure> dayTypeRefStructElement = objectFactory.createDayTypeRef(dayTypeRefStruct);
            dayTypeStructure.getDayTypeRef().add(dayTypeRefStructElement);

            DayTypeAssignment dayTypeAssignment;
            if (!dayTypeAssignments.containsKey(dayTypeId)) {
                dayTypeAssignment = netexObjectFactory.createDayTypeAssignment(dayTypeIdSuffix, i + 1, dateOfOperation, dayTypeId);
                dayTypeAssignments.put(dayTypeAssignment.getId(), dayTypeAssignment);
            }
        }
    }


    private String getIdLinePart() {
        return hashObjectId(localContext.get(AIRLINE_IATA) + StringUtils.remove(localContext.get(LINE_DESIGNATION), DASH), 10);
    }

    private void clearAll() {
        localContext.clear();
        routeIdDesignationMap.clear();
        routeDesignationPatternMap.clear();
        dayTypes.clear();
        dayTypeAssignments.clear();
        operatingPeriods.clear();
        if (netexObjectFactory != null) {
            netexObjectFactory.clearReferentials();
        }
    }
}
