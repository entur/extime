package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.config.NetexStaticDataSet;
import no.rutebanken.extime.config.NetexStaticDataSet.OrganisationDataSet;
import no.rutebanken.extime.model.*;
import no.rutebanken.extime.util.DateUtils;
import no.rutebanken.extime.util.NetexObjectFactory;
import no.rutebanken.extime.util.NetexObjectIdCreator;
import org.rutebanken.netex.model.*;
import org.rutebanken.netex.model.PublicationDeliveryStructure.DataObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Component(value = "scheduledFlightToNetexConverter")
public class ScheduledFlightToNetexConverter {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledFlightToNetexConverter.class);

    public static final String VERSION_ONE = "1";
    private static final String MAIN_VERSION = "1.0";
    private static final String AVINOR_AUTHORITY_ID = "AVI";
    private static final String NSR_AUTHORITY_ID = "NSR";

    private static final String WORK_DAYS_DISPLAY_NAME = "Ukedager (mandag til fredag)";
    private static final String SATURDAY_DISPLAY_NAME = "Helgdag (lørdag)";
    private static final String SUNDAY_DISPLAY_NAME = "Helgdag (søndag)";

    private static final String WORK_DAYS_LABEL = "weekday";
    private static final String SATURDAY_LABEL = "saturday";
    private static final String SUNDAY_LABEL = "sunday";

    private static final String DEFAULT_ZONE_ID = "UTC";
    private static final String DEFAULT_LANGUAGE = "no";
    private static final int DEFAULT_START_INCLUSIVE = 1111111;
    private static final int DEFAULT_END_EXCLUSIVE = 8888888;

    private static final HashMap<DayOfWeek, DayOfWeekEnumeration> dayOfWeekMap = new HashMap<>();

    static {
        dayOfWeekMap.put(DayOfWeek.MONDAY, DayOfWeekEnumeration.MONDAY);
        dayOfWeekMap.put(DayOfWeek.TUESDAY, DayOfWeekEnumeration.TUESDAY);
        dayOfWeekMap.put(DayOfWeek.WEDNESDAY, DayOfWeekEnumeration.WEDNESDAY);
        dayOfWeekMap.put(DayOfWeek.THURSDAY, DayOfWeekEnumeration.THURSDAY);
        dayOfWeekMap.put(DayOfWeek.FRIDAY, DayOfWeekEnumeration.FRIDAY);
        dayOfWeekMap.put(DayOfWeek.SATURDAY, DayOfWeekEnumeration.SATURDAY);
        dayOfWeekMap.put(DayOfWeek.SUNDAY, DayOfWeekEnumeration.SUNDAY);
    }

    @Autowired
    private NetexCommonDataSet netexCommonDataSet;

    @Autowired
    private NetexStaticDataSet netexStaticDataSet;

    @Bean
    public ObjectFactory objectFactory() {
        return new ObjectFactory();
    }

    public JAXBElement<PublicationDeliveryStructure> convertToNetex(ScheduledFlight scheduledFlight) throws Exception {
        OffsetDateTime publicationTimestamp = OffsetDateTime.ofInstant(Instant.now(), ZoneId.of(DEFAULT_ZONE_ID));
        //LocalDate dateOfOperation = scheduledFlight.getDateOfOperation();

        String routePath = String.format("%s-%s", scheduledFlight.getDepartureAirportName(), scheduledFlight.getArrivalAirportName());
        String flightId = scheduledFlight.getAirlineFlightId();

        List<StopPlace> stopPlaces = createStopPlaces(scheduledFlight);
        List<ScheduledStopPoint> scheduledStopPoints = createScheduledStopPoints(scheduledFlight);
        List<RoutePoint> routePoints = createRoutePoints(scheduledFlight);
        List<PassengerStopAssignment> stopAssignments = createStopAssignments(scheduledStopPoints);

        Route route = createRoute(flightId, routePath, scheduledFlight);
        Operator operator = resolveOperatorFromIATA(scheduledFlight.getAirlineIATA());
        Line line = createLine(route, flightId, routePath, operator);
        JourneyPattern journeyPattern = createJourneyPattern(route, scheduledFlight);

        List<DayType> dayTypes = createDayTypes(scheduledFlight.getWeekDaysPattern(), flightId);
        List<ServiceJourney> serviceJourneys = createServiceJourneyList(scheduledFlight, dayTypes, journeyPattern, line);

        Frames_RelStructure frames = objectFactory().createFrames_RelStructure();
        frames.getCommonFrame().add(createResourceFrame(operator));
        frames.getCommonFrame().add(createSiteFrame(stopPlaces));

        JAXBElement<ServiceFrame> serviceFrame = createServiceFrame(publicationTimestamp,
                scheduledFlight.getAirlineName(), scheduledFlight.getAirlineIATA(), flightId, routePoints, route, line,
                scheduledStopPoints, journeyPattern, stopAssignments);
        frames.getCommonFrame().add(serviceFrame);

        frames.getCommonFrame().add(createTimetableFrame(scheduledFlight.getAvailabilityPeriod(), serviceJourneys));
        frames.getCommonFrame().add(createServiceCalendarFrame(dayTypes));

        //cleanStopPointsFromTempValues(scheduledStopPoints);

        JAXBElement<CompositeFrame> compositeFrame = createCompositeFrame(publicationTimestamp, flightId, frames);
        PublicationDeliveryStructure publicationDeliveryStructure = createPublicationDeliveryStructure(publicationTimestamp, compositeFrame, flightId, routePath);
        return objectFactory().createPublicationDelivery(publicationDeliveryStructure);
    }

    public PublicationDeliveryStructure createPublicationDeliveryStructure(OffsetDateTime publicationTimestamp,
            JAXBElement<CompositeFrame> compositeFrame, String flightId, String routePath) {
        OrganisationDataSet avinorDataSet = netexStaticDataSet.getOrganisations().get(AVINOR_AUTHORITY_ID.toLowerCase());

        DataObjects dataObjects = objectFactory().createPublicationDeliveryStructureDataObjects();
        dataObjects.getCompositeFrameOrCommonFrame().add(compositeFrame);

        return objectFactory().createPublicationDeliveryStructure()
                .withVersion(MAIN_VERSION)
                .withPublicationTimestamp(publicationTimestamp)
                .withParticipantRef(avinorDataSet.getName())
                .withDescription(createMultilingualString(String.format("Flight %s : %s", flightId, routePath)))
                .withDataObjects(dataObjects);
    }

    public JAXBElement<CompositeFrame> createCompositeFrame(OffsetDateTime publicationTimestamp, String flightId, Frames_RelStructure frames) {
        Codespaces_RelStructure codespaces = objectFactory().createCodespaces_RelStructure()
                .withCodespaceRefOrCodespace(Arrays.asList(avinorCodespace(), nsrCodespace()));

        LocaleStructure localeStructure = objectFactory().createLocaleStructure()
                .withTimeZone(DEFAULT_ZONE_ID)
                .withDefaultLanguage(DEFAULT_LANGUAGE);

        VersionFrameDefaultsStructure versionFrameDefaultsStructure = objectFactory().createVersionFrameDefaultsStructure()
                .withDefaultLocale(localeStructure);

        String compositeFrameId = NetexObjectIdCreator.createCompositeFrameId(AVINOR_AUTHORITY_ID, flightId);

        CompositeFrame compositeFrame = objectFactory().createCompositeFrame()
                .withVersion(VERSION_ONE)
                .withCreated(publicationTimestamp)
                .withId(compositeFrameId)
                .withCodespaces(codespaces)
                .withFrameDefaults(versionFrameDefaultsStructure)
                .withFrames(frames);

        return objectFactory().createCompositeFrame(compositeFrame);
    }

    public JAXBElement<ResourceFrame> createResourceFrame(Operator operator) {
        OrganisationsInFrame_RelStructure organisationsInFrame = objectFactory().createOrganisationsInFrame_RelStructure();
        organisationsInFrame.getOrganisation_().add(objectFactory().createAuthority(createAuthority()));
        organisationsInFrame.getOrganisation_().add(objectFactory().createOperator(operator));

        // TODO generate object id
        String resourceFrameId = NetexObjectIdCreator.createResourceFrameId(AVINOR_AUTHORITY_ID, "1");

        ResourceFrame resourceFrame = objectFactory().createResourceFrame()
                .withVersion(VERSION_ONE)
                .withId(resourceFrameId)
                .withOrganisations(organisationsInFrame);

        return objectFactory().createResourceFrame(resourceFrame);
    }

    public JAXBElement<SiteFrame> createSiteFrame(List<StopPlace> stopPlaces) {
        StopPlacesInFrame_RelStructure stopPlacesInFrameRelStructure = objectFactory().createStopPlacesInFrame_RelStructure()
                        .withStopPlace(stopPlaces);

        // TODO generate object id
        String siteFrameId = NetexObjectIdCreator.createSiteFrameId(AVINOR_AUTHORITY_ID, "1");

        SiteFrame siteFrame = objectFactory().createSiteFrame()
                .withVersion(VERSION_ONE)
                .withId(siteFrameId)
                .withStopPlaces(stopPlacesInFrameRelStructure);

        return objectFactory().createSiteFrame(siteFrame);
    }

    public JAXBElement<ServiceFrame> createServiceFrame(OffsetDateTime publicationTimestamp, String airlineName,
            String airlineIata, String flightId, List<RoutePoint> routePoints, Route route, Line line,
            List<ScheduledStopPoint> scheduledStopPoints, JourneyPattern journeyPattern, List<PassengerStopAssignment> stopAssignments) {

        String networkId = NetexObjectIdCreator.createNetworkId(AVINOR_AUTHORITY_ID, airlineIata);

        Network network = objectFactory().createNetwork()
                .withVersion(VERSION_ONE)
                .withChanged(publicationTimestamp)
                .withId(networkId)
                .withName(createMultilingualString(airlineName));
                //.withTransportOrganisationRef(organisationRefStructElement); // schema validation requires 'abstract' attribute set to false (not available in netex model)

        RoutePointsInFrame_RelStructure routePointsInFrame = objectFactory().createRoutePointsInFrame_RelStructure()
                .withRoutePoint(routePoints);

        RoutesInFrame_RelStructure routesInFrame = objectFactory().createRoutesInFrame_RelStructure();
        routesInFrame.getRoute_().add(objectFactory().createRoute(route));

        LinesInFrame_RelStructure linesInFrame = objectFactory().createLinesInFrame_RelStructure();
        linesInFrame.getLine_().add(objectFactory().createLine(line));

        ScheduledStopPointsInFrame_RelStructure scheduledStopPointsInFrame = objectFactory().createScheduledStopPointsInFrame_RelStructure()
                .withScheduledStopPoint(scheduledStopPoints);

        JourneyPatternsInFrame_RelStructure journeyPatternsInFrame = objectFactory().createJourneyPatternsInFrame_RelStructure()
                .withJourneyPattern_OrJourneyPatternView(objectFactory().createJourneyPattern(journeyPattern));

        StopAssignmentsInFrame_RelStructure stopAssignmentsInFrame = objectFactory().createStopAssignmentsInFrame_RelStructure();
        stopAssignments.forEach(stopAssignment -> stopAssignmentsInFrame.getStopAssignment().add(objectFactory().createPassengerStopAssignment(stopAssignment)));

        String serviceFrameId = NetexObjectIdCreator.createServiceFrameId(AVINOR_AUTHORITY_ID, flightId);

        ServiceFrame serviceFrame = objectFactory().createServiceFrame()
                .withVersion(VERSION_ONE)
                .withId(serviceFrameId)
                .withNetwork(network)
                .withRoutePoints(routePointsInFrame)
                .withRoutes(routesInFrame)
                .withLines(linesInFrame)
                .withScheduledStopPoints(scheduledStopPointsInFrame)
                .withStopAssignments(stopAssignmentsInFrame)
                .withJourneyPatterns(journeyPatternsInFrame);

        return objectFactory().createServiceFrame(serviceFrame);
    }

    public JAXBElement<ServiceCalendarFrame> createServiceCalendarFrame(List<DayType> dayTypes) {
        DayTypesInFrame_RelStructure dayTypesStructure = objectFactory().createDayTypesInFrame_RelStructure();
        dayTypes.forEach(dayType -> dayTypesStructure.getDayType_().add(objectFactory().createDayType(dayType)));

        // TODO generate objectId
        String serviceCalendarFrameId = NetexObjectIdCreator.createServiceCalendarFrameId(AVINOR_AUTHORITY_ID, "1");

        ServiceCalendarFrame serviceCalendarFrame = objectFactory().createServiceCalendarFrame()
                .withVersion(VERSION_ONE)
                .withId(serviceCalendarFrameId)
                .withDayTypes(dayTypesStructure);

        return objectFactory().createServiceCalendarFrame(serviceCalendarFrame);
    }

    public JAXBElement<TimetableFrame> createTimetableFrame(AvailabilityPeriod availabilityPeriod, List<ServiceJourney> serviceJourneys) {
        ValidityConditions_RelStructure validityConditionsRelStructure = objectFactory().createValidityConditions_RelStructure()
                .withValidityConditionRefOrValidBetweenOrValidityCondition_(createAvailabilityCondition(availabilityPeriod));

        JourneysInFrame_RelStructure journeysInFrameRelStructure = objectFactory().createJourneysInFrame_RelStructure();
        journeysInFrameRelStructure.getDatedServiceJourneyOrDeadRunOrServiceJourney().addAll(serviceJourneys);

        // TODO generate objectId
        String timetableFrameId = NetexObjectIdCreator.createTimetableFrameId(AVINOR_AUTHORITY_ID, "1");

        TimetableFrame timetableFrame = objectFactory().createTimetableFrame()
                .withVersion(VERSION_ONE)
                .withId(timetableFrameId)
                .withValidityConditions(validityConditionsRelStructure)
                .withVehicleJourneys(journeysInFrameRelStructure);

        return objectFactory().createTimetableFrame(timetableFrame);
    }

    private List<DayType> createDayTypes(Set<DayOfWeek> weekDaysPattern, String flightId) {
        Map<Boolean, List<DayOfWeek>> dayOfWeeksByDayType = weekDaysPattern.stream()
                .collect(Collectors.partitioningBy(dayOfWeek -> dayOfWeek.query(DateUtils.WorkDays::isWorkDay)));

        List<DayOfWeek> workDays = dayOfWeeksByDayType.get(Boolean.TRUE);
        List<DayOfWeek> weekendDays = dayOfWeeksByDayType.get(Boolean.FALSE);
        List<DayType> dayTypes = Lists.newArrayList();

        if (!workDays.isEmpty()) {
            dayTypes.add(createDayType(workDays, flightId, WORK_DAYS_LABEL, WORK_DAYS_DISPLAY_NAME));
        }

        if (!weekendDays.isEmpty()) {
            if (weekendDays.contains(DayOfWeek.SATURDAY)) {
                int index = weekendDays.indexOf(DayOfWeek.SATURDAY);
                List<DayOfWeek> saturday = Lists.newArrayList(weekendDays.get(index));
                dayTypes.add(createDayType(saturday, flightId, SATURDAY_LABEL, SATURDAY_DISPLAY_NAME));
            }
            if (weekendDays.contains(DayOfWeek.SUNDAY)) {
                int index = weekendDays.indexOf(DayOfWeek.SUNDAY);
                List<DayOfWeek> sunday = Lists.newArrayList(weekendDays.get(index));
                dayTypes.add(createDayType(sunday, flightId, SUNDAY_LABEL, SUNDAY_DISPLAY_NAME));
            }
        }

        return dayTypes;
    }

    public DayType createDayType(List<DayOfWeek> daysOfWeekPattern, String flightId, String objectId, String name) {
        List<DayOfWeekEnumeration> daysOfWeek = Lists.newArrayList();
        daysOfWeekPattern.forEach(dayOfWeek -> daysOfWeek.add(dayOfWeekMap.get(dayOfWeek)));

        PropertyOfDay propertyOfDayWeekDays = objectFactory().createPropertyOfDay();
        propertyOfDayWeekDays.getDaysOfWeek().addAll(daysOfWeek);

        PropertiesOfDay_RelStructure propertiesOfDay = objectFactory().createPropertiesOfDay_RelStructure()
                .withPropertyOfDay(propertyOfDayWeekDays);

        String dayTypeId = NetexObjectIdCreator.createDayTypeId(flightId, objectId);

        return objectFactory().createDayType()
                .withVersion(VERSION_ONE)
                .withId(dayTypeId)
                .withName(createMultilingualString(name))
                .withProperties(propertiesOfDay);
    }

    public List<StopPlace> createStopPlaces(ScheduledFlight scheduledFlight) throws Exception {
        Map<String, StopPlace> stopPlaceMap = netexCommonDataSet.getStopPlaceMap();

        if (scheduledFlight instanceof ScheduledDirectFlight) {

            String departureAirportIATA = scheduledFlight.getDepartureAirportIATA();
            String arrivalAirportIATA = scheduledFlight.getArrivalAirportIATA();

            StopPlace departureStopPlace = stopPlaceMap.get(departureAirportIATA);
            StopPlace arrivalStopPlace = stopPlaceMap.get(arrivalAirportIATA);

            return Lists.newArrayList(departureStopPlace, arrivalStopPlace);

        } else if (scheduledFlight instanceof ScheduledStopoverFlight) {

            List<ScheduledStopover> scheduledStopovers = ((ScheduledStopoverFlight) scheduledFlight).getScheduledStopovers();
            List<StopPlace> stopPlaces = new ArrayList<>();

            Set<String> iataCodes = scheduledStopovers.stream()
                    .map(ScheduledStopover::getAirportIATA)
                    .collect(Collectors.toSet());

            for (String iata : iataCodes) {
                StopPlace stopPlace = stopPlaceMap.get(iata);
                stopPlaces.add(stopPlace);
            }

            return stopPlaces;

        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }
    }

    public JAXBElement<AvailabilityCondition> createAvailabilityCondition(AvailabilityPeriod availabilityPeriod) {
        // TODO generate objectId
        String availabilityConditionId = NetexObjectIdCreator.createAvailabilityConditionId(AVINOR_AUTHORITY_ID, "1");

        AvailabilityCondition availabilityCondition = objectFactory().createAvailabilityCondition()
                .withVersion(VERSION_ONE)
                .withId(availabilityConditionId)
                .withFromDate(availabilityPeriod.getPeriodFromDateTime())
                .withToDate(availabilityPeriod.getPeriodToDateTime());

        return objectFactory().createAvailabilityCondition(availabilityCondition);
    }

    public List<ServiceJourney> createServiceJourneyList(ScheduledFlight scheduledFlight, List<DayType> dayTypes,
            JourneyPattern journeyPattern, Line line) throws IllegalArgumentException {

        List<ServiceJourney> serviceJourneyList = new ArrayList<>();
        TimetabledPassingTimes_RelStructure passingTimesRelStructure = objectFactory().createTimetabledPassingTimes_RelStructure();

        PointsInJourneyPattern_RelStructure pointsInSequence = journeyPattern.getPointsInSequence();
        List<PointInLinkSequence_VersionedChildStructure> pointsInLinkSequence = pointsInSequence
                .getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern();

        JourneyPatternRefStructure journeyPatternRefStructure = objectFactory().createJourneyPatternRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(journeyPattern.getId());
        JAXBElement<JourneyPatternRefStructure> journeyPatternRefStructureElement = objectFactory().createJourneyPatternRef(journeyPatternRefStructure);

        DayTypeRefs_RelStructure dayTypeStructure = objectFactory().createDayTypeRefs_RelStructure();

        dayTypes.forEach(dayType -> {
            DayTypeRefStructure dayTypeRefStruct = NetexObjectFactory.createDayTypeRefStructure(dayType.getId());
            dayTypeStructure.getDayTypeRef().add(objectFactory().createDayTypeRef(dayTypeRefStruct));
        });

        if (scheduledFlight instanceof ScheduledDirectFlight) {
            TimetabledPassingTime departurePassingTime = NetexObjectFactory.createTimetabledPassingTime(pointsInLinkSequence.get(0).getId());
            departurePassingTime.setDepartureTime(scheduledFlight.getTimeOfDeparture());
            passingTimesRelStructure.withTimetabledPassingTime(departurePassingTime);

            TimetabledPassingTime arrivalPassingTime = NetexObjectFactory.createTimetabledPassingTime(pointsInLinkSequence.get(1).getId());
            arrivalPassingTime.setArrivalTime(scheduledFlight.getTimeOfArrival());
            passingTimesRelStructure.withTimetabledPassingTime(arrivalPassingTime);

            // TODO move to factory
            LineRefStructure lineRefStruct = objectFactory().createLineRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(line.getId());
            JAXBElement<LineRefStructure> lineRefStructElement = objectFactory().createLineRef(lineRefStruct);

            String serviceJourneyId = NetexObjectIdCreator.createServiceJourneyId(AVINOR_AUTHORITY_ID, scheduledFlight.getAirlineFlightId());

            // TODO move to factory
            ServiceJourney serviceJourney = objectFactory().createServiceJourney()
                    .withVersion(VERSION_ONE)
                    .withId(serviceJourneyId)
                    .withPublicCode(scheduledFlight.getAirlineFlightId())
                    .withDepartureTime(scheduledFlight.getTimeOfDeparture())
                    .withDayTypes(dayTypeStructure)
                    .withJourneyPatternRef(journeyPatternRefStructureElement)
                    .withLineRef(lineRefStructElement)
                    .withPassingTimes(passingTimesRelStructure);

            serviceJourneyList.add(serviceJourney);
            return serviceJourneyList;

        } else if (scheduledFlight instanceof ScheduledStopoverFlight) {
            List<ScheduledStopover> scheduledStopovers = ((ScheduledStopoverFlight) scheduledFlight).getScheduledStopovers();
            Iterator<ScheduledStopover> stopoverIterator = scheduledStopovers.iterator();
            Iterator<PointInLinkSequence_VersionedChildStructure> pointInLinkSequenceIterator = pointsInLinkSequence.iterator();

            while (stopoverIterator.hasNext() && pointInLinkSequenceIterator.hasNext()) {
                ScheduledStopover scheduledStopover = stopoverIterator.next();
                PointInLinkSequence_VersionedChildStructure pointInLinkSequence = pointInLinkSequenceIterator.next();
                TimetabledPassingTime passingTime = NetexObjectFactory.createTimetabledPassingTime(pointInLinkSequence.getId());

                if (scheduledStopover.getArrivalTime() != null) {
                    passingTime.setArrivalTime(scheduledStopover.getArrivalTime());
                }
                if (scheduledStopover.getDepartureTime() != null) {
                    passingTime.setDepartureTime(scheduledStopover.getDepartureTime());
                }

                passingTimesRelStructure.withTimetabledPassingTime(passingTime);
            }

            // TODO move to factory
            LineRefStructure lineRefStruct = objectFactory().createLineRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(line.getId());
            JAXBElement<LineRefStructure> lineRefStructElement = objectFactory().createLineRef(lineRefStruct);

            String serviceJourneyId = NetexObjectIdCreator.createServiceJourneyId(AVINOR_AUTHORITY_ID, scheduledFlight.getAirlineFlightId());

            // TODO move to factory
            ServiceJourney serviceJourney = objectFactory().createServiceJourney()
                    .withVersion(VERSION_ONE)
                    .withId(serviceJourneyId)
                    .withPublicCode(scheduledFlight.getAirlineFlightId())
                    .withDepartureTime(scheduledStopovers.get(0).getDepartureTime())
                    .withDayTypes(dayTypeStructure)
                    .withJourneyPatternRef(journeyPatternRefStructureElement)
                    .withLineRef(lineRefStructElement)
                    .withPassingTimes(passingTimesRelStructure);

            serviceJourneyList.add(serviceJourney);
            return serviceJourneyList;
        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }
    }

    public List<RoutePoint> createRoutePoints(ScheduledFlight scheduledFlight) {
        Map<String, RoutePoint> routePointMap = netexCommonDataSet.getRoutePointMap();

        if (scheduledFlight instanceof ScheduledDirectFlight) {

            RoutePoint departureRoutePoint = routePointMap.get(scheduledFlight.getDepartureAirportIATA());
            RoutePoint arrivalRoutePoint = routePointMap.get(scheduledFlight.getArrivalAirportIATA());
            return Lists.newArrayList(departureRoutePoint, arrivalRoutePoint);

        } else if (scheduledFlight instanceof ScheduledStopoverFlight) {

            List<ScheduledStopover> scheduledStopovers = ((ScheduledStopoverFlight) scheduledFlight).getScheduledStopovers();
            List<RoutePoint> routePoints = new ArrayList<>();

            Set<String> iataCodes = scheduledStopovers.stream()
                    .map(ScheduledStopover::getAirportIATA)
                    .collect(Collectors.toSet());

            for (String iata : iataCodes) {
                RoutePoint routePoint = routePointMap.get(iata);
                routePoints.add(routePoint);
            }

            return routePoints;

        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }
    }

    public Route createRoute(String flightId, String routePath, ScheduledFlight scheduledFlight) {
        Map<String, RoutePoint> routePointMap = netexCommonDataSet.getRoutePointMap();
        PointsOnRoute_RelStructure pointsOnRoute = objectFactory().createPointsOnRoute_RelStructure();

        if (scheduledFlight instanceof ScheduledDirectFlight) {
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, 2);

            RoutePoint departureRoutePoint = routePointMap.get(scheduledFlight.getDepartureAirportIATA());
            RoutePoint arrivalRoutePoint = routePointMap.get(scheduledFlight.getArrivalAirportIATA());

            PointOnRoute departurePointOnRoute = NetexObjectFactory.createPointOnRoute(
                    idSequence[0], departureRoutePoint.getId());

            PointOnRoute arrivalPointOnRoute = NetexObjectFactory.createPointOnRoute(
                    idSequence[1], arrivalRoutePoint.getId());

            pointsOnRoute.getPointOnRoute().add(departurePointOnRoute);
            pointsOnRoute.getPointOnRoute().add(arrivalPointOnRoute);

        } else if (scheduledFlight instanceof ScheduledStopoverFlight) {
            List<ScheduledStopover> scheduledStopovers = ((ScheduledStopoverFlight) scheduledFlight).getScheduledStopovers();
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, scheduledStopovers.size());

            for (int i = 0; i < scheduledStopovers.size(); i++) {
                ScheduledStopover scheduledStopover = scheduledStopovers.get(i);
                RoutePoint routePoint = routePointMap.get(scheduledStopover.getAirportIATA());
                PointOnRoute pointOnRoute = NetexObjectFactory.createPointOnRoute(idSequence[i], routePoint.getId());
                pointsOnRoute.getPointOnRoute().add(pointOnRoute);
            }
        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }

        // TODO find out what to use for route id
        String routeId = NetexObjectIdCreator.createRouteId(AVINOR_AUTHORITY_ID, flightId);

        return objectFactory().createRoute()
                .withVersion(VERSION_ONE)
                .withId(routeId)
                .withName(createMultilingualString(routePath))
                .withPointsInSequence(pointsOnRoute);
    }

    // TODO move to factory class
    private Line createLine(Route route, String flightId, String routePath, Operator operator) {
        RouteRefStructure routeRefStructure = NetexObjectFactory.createRouteRefStructure(route.getId());
        RouteRefs_RelStructure routeRefs = objectFactory().createRouteRefs_RelStructure()
                .withRouteRef(routeRefStructure);

        OperatorRefStructure operatorRefStructure = NetexObjectFactory.createOperatorRefStructure(operator.getId());
        String lineId = NetexObjectIdCreator.createLineId(AVINOR_AUTHORITY_ID, flightId);

        return objectFactory().createLine()
                .withVersion(VERSION_ONE)
                .withId(lineId)
                .withName(createMultilingualString(routePath))
                .withTransportMode(AllVehicleModesOfTransportEnumeration.AIR)
                .withPublicCode(flightId)
                .withOperatorRef(operatorRefStructure)
                .withRoutes(routeRefs);
    }

    private List<ScheduledStopPoint> createScheduledStopPoints(ScheduledFlight scheduledFlight) throws IllegalArgumentException {
        Map<String, ScheduledStopPoint> stopPointMap = netexCommonDataSet.getStopPointMap();

        if (scheduledFlight instanceof ScheduledDirectFlight) {
            ScheduledStopPoint scheduledDepartureStopPoint = stopPointMap.get(scheduledFlight.getDepartureAirportIATA());
            ScheduledStopPoint scheduledArrivalStopPoint = stopPointMap.get(scheduledFlight.getArrivalAirportIATA());
            return Lists.newArrayList(scheduledDepartureStopPoint, scheduledArrivalStopPoint);

        } else if (scheduledFlight instanceof ScheduledStopoverFlight) {

            List<ScheduledStopover> scheduledStopovers = ((ScheduledStopoverFlight) scheduledFlight).getScheduledStopovers();
            List<ScheduledStopPoint> scheduledStopPoints = new ArrayList<>();

            Set<String> iataCodes = scheduledStopovers.stream()
                    .map(ScheduledStopover::getAirportIATA)
                    .collect(Collectors.toSet());

            for (String iata : iataCodes) {
                ScheduledStopPoint stopPoint = stopPointMap.get(iata);
                scheduledStopPoints.add(stopPoint);
            }

            return scheduledStopPoints;

        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }
    }

    public JourneyPattern createJourneyPattern(Route route, ScheduledFlight scheduledFlight) {
        Map<String, ScheduledStopPoint> stopPointMap = netexCommonDataSet.getStopPointMap();
        PointsInJourneyPattern_RelStructure pointsInJourneyPattern = objectFactory().createPointsInJourneyPattern_RelStructure();

        if (scheduledFlight instanceof ScheduledDirectFlight) {
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, 2);

            ScheduledStopPoint departureStopPoint = stopPointMap.get(scheduledFlight.getDepartureAirportIATA());
            ScheduledStopPoint arrivalStopPoint = stopPointMap.get(scheduledFlight.getArrivalAirportIATA());

            StopPointInJourneyPattern departureStopPointInJourneyPattern = NetexObjectFactory.createStopPointInJourneyPattern(
                    idSequence[0], BigInteger.valueOf(1L), departureStopPoint.getId());
            departureStopPointInJourneyPattern.setForAlighting(Boolean.FALSE);

            StopPointInJourneyPattern arrivalStopPointInJourneyPattern = NetexObjectFactory.createStopPointInJourneyPattern(
                    idSequence[1], BigInteger.valueOf(2L), arrivalStopPoint.getId());
            arrivalStopPointInJourneyPattern.setForBoarding(Boolean.FALSE);

            pointsInJourneyPattern.getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()
                    .add(departureStopPointInJourneyPattern);

            pointsInJourneyPattern.getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()
                    .add(arrivalStopPointInJourneyPattern);

        } else if (scheduledFlight instanceof ScheduledStopoverFlight) {
            List<ScheduledStopover> scheduledStopovers = ((ScheduledStopoverFlight) scheduledFlight).getScheduledStopovers();
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, scheduledStopovers.size());

            for (int i = 0; i < scheduledStopovers.size(); i++) {
                ScheduledStopover scheduledStopover = scheduledStopovers.get(i);
                ScheduledStopPoint scheduledStopPoint = stopPointMap.get(scheduledStopover.getAirportIATA());

                StopPointInJourneyPattern stopPointInJourneyPattern = NetexObjectFactory.createStopPointInJourneyPattern(
                        idSequence[i], BigInteger.valueOf(i + 1), scheduledStopPoint.getId());

                if (i == 0) {
                    stopPointInJourneyPattern.setForAlighting(false);
                }

                if (i == scheduledStopovers.size() - 1) {
                    stopPointInJourneyPattern.setForBoarding(false);
                }

                pointsInJourneyPattern.getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()
                        .add(stopPointInJourneyPattern);
            }

        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }

        RouteRefStructure routeRefStructure = objectFactory().createRouteRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(route.getId());

        int journeyPatternObjectId = NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE);
        String journeyPatternId = NetexObjectIdCreator.createJourneyPatternId(AVINOR_AUTHORITY_ID, String.valueOf(journeyPatternObjectId));

        return objectFactory().createJourneyPattern()
                .withVersion(VERSION_ONE)
                .withId(journeyPatternId)
                .withRouteRef(routeRefStructure)
                .withPointsInSequence(pointsInJourneyPattern);
    }

    public List<PassengerStopAssignment> createStopAssignments(List<ScheduledStopPoint> scheduledStopPoints) {
        List<PassengerStopAssignment> stopAssignments = new ArrayList<>(scheduledStopPoints.size());

        for (ScheduledStopPoint scheduledStopPoint : scheduledStopPoints) {
            PassengerStopAssignment stopAssignment = netexCommonDataSet.getStopAssignmentMap().get(scheduledStopPoint.getShortName().getValue());
            stopAssignments.add(stopAssignment);
        }

        return stopAssignments;
    }

    // TODO move to factory class
    public MultilingualString createMultilingualString(String value) {
        return objectFactory().createMultilingualString().withValue(value);
    }

    public Operator resolveOperatorFromIATA(String airlineIata) {
        Map<String, OrganisationDataSet> organisations = netexStaticDataSet.getOrganisations();
        OrganisationDataSet organisationDataSet = organisations.get(airlineIata.toLowerCase());

        return organisationDataSet != null ?
                createKnownOperator(airlineIata, organisationDataSet) :
                createUnknowOperator(airlineIata);
    }

    // TODO move to factory class
    public Codespace avinorCodespace() {
        OrganisationDataSet avinorDataSet = netexStaticDataSet.getOrganisations().get(AVINOR_AUTHORITY_ID.toLowerCase());

        return objectFactory().createCodespace()
                .withId(avinorDataSet.getName().toLowerCase())
                .withXmlns(AVINOR_AUTHORITY_ID)
                .withXmlnsUrl(avinorDataSet.getUrl());
    }

    // TODO move to factory class
    public Codespace nsrCodespace() {
        OrganisationDataSet nsrDataSet = netexStaticDataSet.getOrganisations().get(NSR_AUTHORITY_ID.toLowerCase());

        return objectFactory().createCodespace()
                .withId(NSR_AUTHORITY_ID.toLowerCase())
                .withXmlns(NSR_AUTHORITY_ID)
                .withXmlnsUrl(nsrDataSet.getUrl());
    }

    // TODO move to factory class
    public Authority createAuthority() {
        OrganisationDataSet avinorDataSet = netexStaticDataSet.getOrganisations().get(AVINOR_AUTHORITY_ID.toLowerCase());

        List<JAXBElement<?>> organisationRest = createOrganisationRest(
                avinorDataSet.getCompanyNumber(),
                avinorDataSet.getName(),
                avinorDataSet.getLegalName(),
                avinorDataSet.getPhone(),
                avinorDataSet.getUrl(),
                OrganisationTypeEnumeration.AUTHORITY
        );

        String authorityId = NetexObjectIdCreator.createAuthorityId(AVINOR_AUTHORITY_ID, avinorDataSet.getName());

        return objectFactory().createAuthority()
                .withVersion(VERSION_ONE)
                .withId(authorityId)
                .withRest(organisationRest);
    }

    // TODO move to factory class
    public Operator createKnownOperator(String airlineIata, OrganisationDataSet organisationDataSet) {
        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                organisationDataSet.getCompanyNumber(),
                organisationDataSet.getName(),
                organisationDataSet.getLegalName(),
                organisationDataSet.getPhone(),
                organisationDataSet.getUrl(),
                OrganisationTypeEnumeration.OPERATOR
        );

        String operatorId = NetexObjectIdCreator.createOperatorId(AVINOR_AUTHORITY_ID, airlineIata);

        return objectFactory().createOperator()
                .withVersion(VERSION_ONE)
                .withId(operatorId)
                .withRest(operatorRest);
    }

    private Operator createUnknowOperator(String airlineIata) {
        //logUnknownOperator(airlineIata);

        List<JAXBElement<?>> dummyOperatorRest = createOrganisationRest(
                "999999999",
                airlineIata,
                airlineIata,
                "0047 999 99 999",
                String.format("http://%s.no/", airlineIata),
                OrganisationTypeEnumeration.OPERATOR
        );

        String operatorId = NetexObjectIdCreator.createOperatorId(AVINOR_AUTHORITY_ID, airlineIata);

        return objectFactory().createOperator()
                .withVersion(VERSION_ONE)
                .withId(operatorId)
                .withRest(dummyOperatorRest);
    }

    public void logUnknownOperator(String airlineIata) {
        logger.warn("Unknown operator identified by id : {}", airlineIata);

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter("/Users/swirzen/dev/git/extime/target/unknow-operators.dat", true));
            writer.write("Unknown operator identified by id : " + airlineIata.toUpperCase());
            writer.newLine();
            writer.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            if (writer != null) try {
                writer.close();
            } catch (IOException ioe2) {
            }
        }
    }

    // TODO move to factory class
    public List<JAXBElement<?>> createOrganisationRest(String companyNumber, String name, String legalName,
                                                       String phone, String url, OrganisationTypeEnumeration organisationType) {
        JAXBElement<String> companyNumberStructure = objectFactory()
                .createOrganisation_VersionStructureCompanyNumber(companyNumber);
        JAXBElement<MultilingualString> nameStructure = objectFactory()
                .createOrganisation_VersionStructureName(createMultilingualString(name));
        JAXBElement<MultilingualString> legalNameStructure = objectFactory()
                .createOrganisation_VersionStructureLegalName(createMultilingualString(legalName));
        JAXBElement<ContactStructure> contactStructure = createContactStructure(phone, url);
        JAXBElement<List<OrganisationTypeEnumeration>> organisationTypes = objectFactory()
                .createOrganisation_VersionStructureOrganisationType(Collections.singletonList(organisationType));
        return Lists.newArrayList(companyNumberStructure, nameStructure, legalNameStructure, contactStructure, organisationTypes);
    }

    // TODO move to factory class
    public JAXBElement<ContactStructure> createContactStructure(String phone, String url) {
        ContactStructure contactStructure = objectFactory().createContactStructure()
                .withPhone(phone)
                .withUrl(url);

        return objectFactory().createOrganisation_VersionStructureContactDetails(contactStructure);
    }

    private void cleanStopPointsFromTempValues(List<ScheduledStopPoint> scheduledStopPoints) {
        scheduledStopPoints.forEach(stopPoint -> stopPoint.setShortName(null));
    }

}
