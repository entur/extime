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

import static no.rutebanken.extime.Constants.*;

// TODO refactor and use netex object factory methods
@Component(value = "scheduledFlightToNetexConverter")
public class ScheduledFlightToNetexConverter {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledFlightToNetexConverter.class);

    private static final String WORK_DAYS_DISPLAY_NAME = "Ukedager (mandag til fredag)";
    private static final String SATURDAY_DISPLAY_NAME = "Helgdag (lørdag)";
    private static final String SUNDAY_DISPLAY_NAME = "Helgdag (søndag)";

    private static final String WORK_DAYS_LABEL = "weekday";
    private static final String SATURDAY_LABEL = "saturday";
    private static final String SUNDAY_LABEL = "sunday";

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

    @Autowired
    private ObjectFactory objectFactory;

    @Autowired
    private NetexObjectFactory netexObjectFactory;


    public JAXBElement<PublicationDeliveryStructure> convertToNetex(ScheduledFlight scheduledFlight) throws Exception {
        OffsetDateTime publicationTimestamp = OffsetDateTime.ofInstant(Instant.now(), ZoneId.of(DEFAULT_ZONE_ID));
        AvailabilityPeriod availabilityPeriod = scheduledFlight.getAvailabilityPeriod();

        String routePath = String.format("%s-%s", scheduledFlight.getDepartureAirportName(), scheduledFlight.getArrivalAirportName());
        String flightId = scheduledFlight.getAirlineFlightId();

        Operator operator = resolveOperatorFromIATA(scheduledFlight.getAirlineIATA());
        Line line = createLine(flightId, routePath, operator);

        // TODO add support for multiple routes per line
        List<RoutePoint> routePoints = createRoutePoints(scheduledFlight);
        Route route = createRoute(flightId, routePath, scheduledFlight, line);
        RouteRefStructure routeRefStructure = netexObjectFactory.createRouteRefStructure(route.getId());
        RouteRefs_RelStructure routeRefStruct = objectFactory.createRouteRefs_RelStructure().withRouteRef(routeRefStructure);
        line.setRoutes(routeRefStruct);

        JourneyPattern journeyPattern = createJourneyPattern(route, scheduledFlight);
        List<DayType> dayTypes = createDayTypes(scheduledFlight.getWeekDaysPattern(), flightId);
        List<ServiceJourney> serviceJourneys = createServiceJourneyList(scheduledFlight, dayTypes, journeyPattern, line);

        Frames_RelStructure frames = objectFactory.createFrames_RelStructure();

        JAXBElement<ServiceFrame> serviceFrame = createServiceFrame(publicationTimestamp, scheduledFlight.getAirlineName(),
                scheduledFlight.getAirlineIATA(), flightId, routePoints, route, line, journeyPattern);
        frames.getCommonFrame().add(serviceFrame);

        JAXBElement<TimetableFrame> timetableFrame = createTimetableFrame(availabilityPeriod, serviceJourneys);
        frames.getCommonFrame().add(timetableFrame);

        JAXBElement<ServiceCalendarFrame> serviceCalendarFrame = createServiceCalendarFrame(availabilityPeriod, dayTypes);
        frames.getCommonFrame().add(serviceCalendarFrame);

        //cleanStopPointsFromTempValues(scheduledStopPoints); // TODO fix this to remove short names from stop points, or move to common converter

        JAXBElement<CompositeFrame> compositeFrame = createCompositeFrame(publicationTimestamp, availabilityPeriod, flightId, frames); // TODO refactor and use netex object factory method
        PublicationDeliveryStructure publicationDeliveryStructure = createPublicationDeliveryStructure(publicationTimestamp, compositeFrame, flightId, routePath); // TODO refactor and use netex object factory method
        return objectFactory.createPublicationDelivery(publicationDeliveryStructure);
    }

    public PublicationDeliveryStructure createPublicationDeliveryStructure(OffsetDateTime publicationTimestamp,
            JAXBElement<CompositeFrame> compositeFrame, String flightId, String routePath) {
        OrganisationDataSet avinorDataSet = netexStaticDataSet.getOrganisations().get(AVINOR_XMLNS.toLowerCase());

        DataObjects dataObjects = objectFactory.createPublicationDeliveryStructureDataObjects();
        dataObjects.getCompositeFrameOrCommonFrame().add(compositeFrame);

        return objectFactory.createPublicationDeliveryStructure()
                .withVersion(NETEX_PROFILE_VERSION)
                .withPublicationTimestamp(publicationTimestamp)
                .withParticipantRef(avinorDataSet.getName())
                .withDescription(createMultilingualString(String.format("Flight %s : %s", flightId, routePath)))
                .withDataObjects(dataObjects);
    }

    public JAXBElement<CompositeFrame> createCompositeFrame(OffsetDateTime publicationTimestamp, AvailabilityPeriod availabilityPeriod, String flightId, Frames_RelStructure frames) {
        ValidityConditions_RelStructure validityConditionsStruct = objectFactory.createValidityConditions_RelStructure()
                .withValidityConditionRefOrValidBetweenOrValidityCondition_(createAvailabilityCondition(availabilityPeriod));

        Codespaces_RelStructure codespaces = objectFactory.createCodespaces_RelStructure()
                .withCodespaceRefOrCodespace(Arrays.asList(avinorCodespace(), nsrCodespace()));

        LocaleStructure localeStructure = objectFactory.createLocaleStructure()
                .withTimeZone(DEFAULT_ZONE_ID)
                .withDefaultLanguage(DEFAULT_LANGUAGE);

        VersionFrameDefaultsStructure versionFrameDefaultsStructure = objectFactory.createVersionFrameDefaultsStructure()
                .withDefaultLocale(localeStructure);

        String compositeFrameId = NetexObjectIdCreator.createCompositeFrameId(AVINOR_XMLNS, flightId);

        CompositeFrame compositeFrame = objectFactory.createCompositeFrame()
                .withVersion(VERSION_ONE)
                .withCreated(publicationTimestamp)
                .withId(compositeFrameId)
                .withValidityConditions(validityConditionsStruct)
                .withCodespaces(codespaces)
                .withFrameDefaults(versionFrameDefaultsStructure)
                .withFrames(frames);

        return objectFactory.createCompositeFrame(compositeFrame);
    }

    // TODO consider using this for 'unknown/new' operators
    public JAXBElement<ResourceFrame> createResourceFrame(Operator operator) {
        OrganisationsInFrame_RelStructure organisationsInFrame = objectFactory.createOrganisationsInFrame_RelStructure();
        organisationsInFrame.getOrganisation_().add(netexObjectFactory.createAvinorAuthorityElement());
        organisationsInFrame.getOrganisation_().add(netexObjectFactory.createNsrAuthorityElement());

        // TODO find out how to add unknown operators
        //organisationsInFrame.getOrganisation_().add(netexObjectFactory.createAirlineOperatorElement(""));

        String resourceFrameId = NetexObjectIdCreator.createResourceFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        ResourceFrame resourceFrame = objectFactory.createResourceFrame()
                .withVersion(VERSION_ONE)
                .withId(resourceFrameId)
                .withOrganisations(organisationsInFrame);

        return objectFactory.createResourceFrame(resourceFrame);
    }

    // TODO remove?
    public JAXBElement<SiteFrame> createSiteFrame(List<StopPlace> stopPlaces) {
        StopPlacesInFrame_RelStructure stopPlacesInFrameRelStructure = objectFactory.createStopPlacesInFrame_RelStructure()
                        .withStopPlace(stopPlaces);

        String siteFrameId = NetexObjectIdCreator.createSiteFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        SiteFrame siteFrame = objectFactory.createSiteFrame()
                .withVersion(VERSION_ONE)
                .withId(siteFrameId)
                .withStopPlaces(stopPlacesInFrameRelStructure);

        return objectFactory.createSiteFrame(siteFrame);
    }

    public JAXBElement<ServiceFrame> createServiceFrame(OffsetDateTime publicationTimestamp, String airlineName,
            String airlineIata, String flightId, List<RoutePoint> routePoints, Route route, Line line, JourneyPattern journeyPattern) {

        String networkId = NetexObjectIdCreator.createNetworkId(AVINOR_XMLNS, airlineIata);

        Network network = objectFactory.createNetwork()
                .withVersion(VERSION_ONE)
                .withChanged(publicationTimestamp)
                .withId(networkId)
                .withName(createMultilingualString(airlineName));

        RoutePointsInFrame_RelStructure routePointsInFrame = objectFactory.createRoutePointsInFrame_RelStructure()
                .withRoutePoint(routePoints);

        RoutesInFrame_RelStructure routesInFrame = objectFactory.createRoutesInFrame_RelStructure();
        routesInFrame.getRoute_().add(objectFactory.createRoute(route));

        LinesInFrame_RelStructure linesInFrame = objectFactory.createLinesInFrame_RelStructure();
        linesInFrame.getLine_().add(objectFactory.createLine(line));

        JourneyPatternsInFrame_RelStructure journeyPatternsInFrame = objectFactory.createJourneyPatternsInFrame_RelStructure()
                .withJourneyPattern_OrJourneyPatternView(objectFactory.createJourneyPattern(journeyPattern));

        String serviceFrameId = NetexObjectIdCreator.createServiceFrameId(AVINOR_XMLNS, flightId);

        ServiceFrame serviceFrame = objectFactory.createServiceFrame()
                .withVersion(VERSION_ONE)
                .withId(serviceFrameId)
                .withNetwork(network)
                .withRoutePoints(routePointsInFrame)
                .withRoutes(routesInFrame)
                .withLines(linesInFrame)
                .withJourneyPatterns(journeyPatternsInFrame);

        return objectFactory.createServiceFrame(serviceFrame);
    }

    // TODO implement operating period on calendar frame
    public JAXBElement<ServiceCalendarFrame> createServiceCalendarFrame(AvailabilityPeriod availabilityPeriod, List<DayType> dayTypes) {
        ValidityConditions_RelStructure validityConditionsStruct = objectFactory.createValidityConditions_RelStructure()
                .withValidityConditionRefOrValidBetweenOrValidityCondition_(createAvailabilityCondition(availabilityPeriod));

        DayTypesInFrame_RelStructure dayTypesStructure = objectFactory.createDayTypesInFrame_RelStructure();
        dayTypes.forEach(dayType -> dayTypesStructure.getDayType_().add(objectFactory.createDayType(dayType)));

        String serviceCalendarFrameId = NetexObjectIdCreator.createServiceCalendarFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        ServiceCalendarFrame serviceCalendarFrame = objectFactory.createServiceCalendarFrame()
                .withVersion(VERSION_ONE)
                .withId(serviceCalendarFrameId)
                .withValidityConditions(validityConditionsStruct)
                .withDayTypes(dayTypesStructure);

        return objectFactory.createServiceCalendarFrame(serviceCalendarFrame);
    }

    public JAXBElement<TimetableFrame> createTimetableFrame(AvailabilityPeriod availabilityPeriod, List<ServiceJourney> serviceJourneys) {
        ValidityConditions_RelStructure validityConditionsStruct = objectFactory.createValidityConditions_RelStructure()
                .withValidityConditionRefOrValidBetweenOrValidityCondition_(createAvailabilityCondition(availabilityPeriod));

        JourneysInFrame_RelStructure journeysInFrameRelStructure = objectFactory.createJourneysInFrame_RelStructure();
        journeysInFrameRelStructure.getDatedServiceJourneyOrDeadRunOrServiceJourney().addAll(serviceJourneys);

        String timetableFrameId = NetexObjectIdCreator.createTimetableFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        TimetableFrame timetableFrame = objectFactory.createTimetableFrame()
                .withVersion(VERSION_ONE)
                .withId(timetableFrameId)
                .withValidityConditions(validityConditionsStruct)
                .withVehicleJourneys(journeysInFrameRelStructure);

        return objectFactory.createTimetableFrame(timetableFrame);
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

        PropertyOfDay propertyOfDayWeekDays = objectFactory.createPropertyOfDay();
        propertyOfDayWeekDays.getDaysOfWeek().addAll(daysOfWeek);

        PropertiesOfDay_RelStructure propertiesOfDay = objectFactory.createPropertiesOfDay_RelStructure()
                .withPropertyOfDay(propertyOfDayWeekDays);

        String dayTypeId = NetexObjectIdCreator.createDayTypeId(AVINOR_XMLNS, String.format("%s_%s", flightId, objectId));

        return objectFactory.createDayType()
                .withVersion(VERSION_ONE)
                .withId(dayTypeId)
                .withName(createMultilingualString(name))
                .withProperties(propertiesOfDay);
    }

    public JAXBElement<AvailabilityCondition> createAvailabilityCondition(AvailabilityPeriod availabilityPeriod) {
        String availabilityConditionId = NetexObjectIdCreator.createAvailabilityConditionId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        AvailabilityCondition availabilityCondition = objectFactory.createAvailabilityCondition()
                .withVersion(VERSION_ONE)
                .withId(availabilityConditionId)
                .withFromDate(availabilityPeriod.getPeriodFromDateTime())
                .withToDate(availabilityPeriod.getPeriodToDateTime());

        return objectFactory.createAvailabilityCondition(availabilityCondition);
    }

    public List<ServiceJourney> createServiceJourneyList(ScheduledFlight scheduledFlight, List<DayType> dayTypes,
            JourneyPattern journeyPattern, Line line) throws IllegalArgumentException {

        List<ServiceJourney> serviceJourneyList = new ArrayList<>();
        TimetabledPassingTimes_RelStructure passingTimesRelStructure = objectFactory.createTimetabledPassingTimes_RelStructure();

        PointsInJourneyPattern_RelStructure pointsInSequence = journeyPattern.getPointsInSequence();
        List<PointInLinkSequence_VersionedChildStructure> pointsInLinkSequence = pointsInSequence
                .getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern();

        JourneyPatternRefStructure journeyPatternRefStructure = objectFactory.createJourneyPatternRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(journeyPattern.getId());
        JAXBElement<JourneyPatternRefStructure> journeyPatternRefStructureElement = objectFactory.createJourneyPatternRef(journeyPatternRefStructure);

        DayTypeRefs_RelStructure dayTypeStructure = objectFactory.createDayTypeRefs_RelStructure();

        dayTypes.forEach(dayType -> {
            DayTypeRefStructure dayTypeRefStruct = netexObjectFactory.createDayTypeRefStructure(dayType.getId());
            dayTypeStructure.getDayTypeRef().add(objectFactory.createDayTypeRef(dayTypeRefStruct));
        });

        if (scheduledFlight instanceof ScheduledDirectFlight) {
            TimetabledPassingTime departurePassingTime = netexObjectFactory.createTimetabledPassingTime(pointsInLinkSequence.get(0).getId());
            departurePassingTime.setDepartureTime(scheduledFlight.getTimeOfDeparture());
            passingTimesRelStructure.withTimetabledPassingTime(departurePassingTime);

            TimetabledPassingTime arrivalPassingTime = netexObjectFactory.createTimetabledPassingTime(pointsInLinkSequence.get(1).getId());
            arrivalPassingTime.setArrivalTime(scheduledFlight.getTimeOfArrival());
            passingTimesRelStructure.withTimetabledPassingTime(arrivalPassingTime);

            // TODO move to factory
            LineRefStructure lineRefStruct = objectFactory.createLineRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(line.getId());
            JAXBElement<LineRefStructure> lineRefStructElement = objectFactory.createLineRef(lineRefStruct);

            String serviceJourneyId = NetexObjectIdCreator.createServiceJourneyId(AVINOR_XMLNS, scheduledFlight.getAirlineFlightId());

            // TODO move to factory
            ServiceJourney serviceJourney = objectFactory.createServiceJourney()
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
                TimetabledPassingTime passingTime = netexObjectFactory.createTimetabledPassingTime(pointInLinkSequence.getId());

                if (scheduledStopover.getArrivalTime() != null) {
                    passingTime.setArrivalTime(scheduledStopover.getArrivalTime());
                }
                if (scheduledStopover.getDepartureTime() != null) {
                    passingTime.setDepartureTime(scheduledStopover.getDepartureTime());
                }

                passingTimesRelStructure.withTimetabledPassingTime(passingTime);
            }

            // TODO move to factory
            LineRefStructure lineRefStruct = objectFactory.createLineRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(line.getId());
            JAXBElement<LineRefStructure> lineRefStructElement = objectFactory.createLineRef(lineRefStruct);

            String serviceJourneyId = NetexObjectIdCreator.createServiceJourneyId(AVINOR_XMLNS, scheduledFlight.getAirlineFlightId());

            // TODO move to factory
            ServiceJourney serviceJourney = objectFactory.createServiceJourney()
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

    public Route createRoute(String flightId, String routePath, ScheduledFlight scheduledFlight, Line line) {
        Map<String, RoutePoint> routePointMap = netexCommonDataSet.getRoutePointMap();
        PointsOnRoute_RelStructure pointsOnRoute = objectFactory.createPointsOnRoute_RelStructure();

        if (scheduledFlight instanceof ScheduledDirectFlight) {
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, 2);

            RoutePoint departureRoutePoint = routePointMap.get(scheduledFlight.getDepartureAirportIATA());
            RoutePoint arrivalRoutePoint = routePointMap.get(scheduledFlight.getArrivalAirportIATA());

            PointOnRoute departurePointOnRoute = netexObjectFactory.createPointOnRoute(
                    idSequence[0], departureRoutePoint.getId());

            PointOnRoute arrivalPointOnRoute = netexObjectFactory.createPointOnRoute(
                    idSequence[1], arrivalRoutePoint.getId());

            pointsOnRoute.getPointOnRoute().add(departurePointOnRoute);
            pointsOnRoute.getPointOnRoute().add(arrivalPointOnRoute);

        } else if (scheduledFlight instanceof ScheduledStopoverFlight) {
            List<ScheduledStopover> scheduledStopovers = ((ScheduledStopoverFlight) scheduledFlight).getScheduledStopovers();
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, scheduledStopovers.size());

            for (int i = 0; i < scheduledStopovers.size(); i++) {
                ScheduledStopover scheduledStopover = scheduledStopovers.get(i);
                RoutePoint routePoint = routePointMap.get(scheduledStopover.getAirportIATA());
                PointOnRoute pointOnRoute = netexObjectFactory.createPointOnRoute(idSequence[i], routePoint.getId());
                pointsOnRoute.getPointOnRoute().add(pointOnRoute);
            }
        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }

        // TODO find out what to use for route id
        String routeId = NetexObjectIdCreator.createRouteId(AVINOR_XMLNS, flightId);

        LineRefStructure lineRefStruct = objectFactory.createLineRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(line.getId());
        JAXBElement<LineRefStructure> lineRefStructElement = objectFactory.createLineRef(lineRefStruct);

        return objectFactory.createRoute()
                .withVersion(VERSION_ONE)
                .withId(routeId)
                .withName(createMultilingualString(routePath))
                .withLineRef(lineRefStructElement)
                .withPointsInSequence(pointsOnRoute);
    }

    // TODO move to factory class
    private Line createLine(String flightId, String routePath, Operator operator) {
        OperatorRefStructure operatorRefStructure = netexObjectFactory.createOperatorRefStructure(operator.getId(), Boolean.FALSE);
        String lineId = NetexObjectIdCreator.createLineId(AVINOR_XMLNS, flightId);

        return objectFactory.createLine()
                .withVersion(VERSION_ONE)
                .withId(lineId)
                .withName(createMultilingualString(routePath))
                .withTransportMode(AllVehicleModesOfTransportEnumeration.AIR)
                .withPublicCode(flightId)
                .withOperatorRef(operatorRefStructure);
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
        PointsInJourneyPattern_RelStructure pointsInJourneyPattern = objectFactory.createPointsInJourneyPattern_RelStructure();

        if (scheduledFlight instanceof ScheduledDirectFlight) {
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, 2);

            ScheduledStopPoint departureStopPoint = stopPointMap.get(scheduledFlight.getDepartureAirportIATA());
            ScheduledStopPoint arrivalStopPoint = stopPointMap.get(scheduledFlight.getArrivalAirportIATA());

            StopPointInJourneyPattern departureStopPointInJourneyPattern = netexObjectFactory.createStopPointInJourneyPattern(
                    idSequence[0], BigInteger.valueOf(1L), departureStopPoint.getId());
            departureStopPointInJourneyPattern.setForAlighting(Boolean.FALSE);

            StopPointInJourneyPattern arrivalStopPointInJourneyPattern = netexObjectFactory.createStopPointInJourneyPattern(
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

                StopPointInJourneyPattern stopPointInJourneyPattern = netexObjectFactory.createStopPointInJourneyPattern(
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

        RouteRefStructure routeRefStructure = objectFactory.createRouteRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(route.getId());

        int journeyPatternObjectId = NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE);
        String journeyPatternId = NetexObjectIdCreator.createJourneyPatternId(AVINOR_XMLNS, String.valueOf(journeyPatternObjectId));

        return objectFactory.createJourneyPattern()
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
        return objectFactory.createMultilingualString().withValue(value);
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
        return objectFactory.createCodespace()
                .withId(NSR_XMLNS.toLowerCase())
                .withXmlns(AVINOR_XMLNS)
                .withXmlnsUrl(AVINOR_XMLNSURL);
    }

    // TODO move to factory class
    public Codespace nsrCodespace() {
        return objectFactory.createCodespace()
                .withId(NSR_XMLNS.toLowerCase())
                .withXmlns(NSR_XMLNS)
                .withXmlnsUrl(NSR_XMLNSURL);
    }

    // TODO move to factory class
    public Operator createKnownOperator(String airlineIata, OrganisationDataSet organisationDataSet) {
        String operatorId = NetexObjectIdCreator.createOperatorId(AVINOR_XMLNS, airlineIata);

        return objectFactory.createOperator()
                .withVersion(VERSION_ONE)
                .withId(operatorId)
                .withCompanyNumber(organisationDataSet.getCompanyNumber())
                .withName(new MultilingualString().withValue(organisationDataSet.getName()))
                .withLegalName(new MultilingualString().withValue(organisationDataSet.getLegalName()))
                .withContactDetails(new ContactStructure().withPhone(organisationDataSet.getPhone()).withUrl(organisationDataSet.getUrl()))
                .withCustomerServiceContactDetails(new ContactStructure().withPhone(organisationDataSet.getPhone()).withUrl(organisationDataSet.getUrl()))
                .withOrganisationType(OrganisationTypeEnumeration.OPERATOR);
    }

    private Operator createUnknowOperator(String airlineIata) {
        //logUnknownOperator(airlineIata);
        String operatorId = NetexObjectIdCreator.createOperatorId(AVINOR_XMLNS, airlineIata);

        return objectFactory.createOperator()
                .withVersion(VERSION_ONE)
                .withId(operatorId)
                .withCompanyNumber("999999999")
                .withName(new MultilingualString().withValue(airlineIata))
                .withLegalName(new MultilingualString().withValue(airlineIata))
                .withContactDetails(new ContactStructure().withPhone("0047 99999999").withUrl(String.format("http://%s.no/", airlineIata)))
                .withContactDetails(new ContactStructure().withPhone("0047 99999999").withUrl(String.format("http://%s.no/", airlineIata)))
                .withOrganisationType(OrganisationTypeEnumeration.OPERATOR);
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


    private void cleanStopPointsFromTempValues(List<ScheduledStopPoint> scheduledStopPoints) {
        scheduledStopPoints.forEach(stopPoint -> stopPoint.setShortName(null));
    }

}
