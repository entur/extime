package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.config.NetexStaticDataSet;
import no.rutebanken.extime.config.NetexStaticDataSet.OrganisationDataSet;
import no.rutebanken.extime.config.NetexStaticDataSet.StopPlaceDataSet;
import no.rutebanken.extime.model.*;
import no.rutebanken.extime.util.DateUtils;
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
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

// TODO go through all string constants in netex generation and add to common class?
// TODO add more logging to this class
// TODO go through exception handling in this class, do throws cleanup
@Component(value = "scheduledFlightToNetexConverter")
public class ScheduledFlightToNetexConverter {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledFlightToNetexConverter.class);

    private static final String VERSION_ONE = "1";
    private static final String AVINOR_AUTHORITY_ID = "AVI";
    private static final String NSR_AUTHORITY_ID = "NSR";
    private static final String WORK_DAYS_DISPLAY_NAME = "Ukedager (mandag til fredag)";
    private static final String WEEKEND_DAYS_DISPLAY_NAME = "Helgdager (lørdag og søndag)";

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

    private ConcurrentMap<String, String> stopPlaceIdCache = new ConcurrentHashMap<>();

    @Autowired
    private NetexStaticDataSet netexStaticDataSet;

    public JAXBElement<PublicationDeliveryStructure> convertToNetex(ScheduledFlight scheduledFlight) throws Exception {
        OffsetDateTime publicationTimestamp = OffsetDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")); // TODO extract UTC string to common constant
        LocalDate dateOfOperation = scheduledFlight.getDateOfOperation();
        String routePath = String.format("%s-%s", scheduledFlight.getDepartureAirportName(), scheduledFlight.getArrivalAirportName());
        String flightId = scheduledFlight.getAirlineFlightId();

        List<StopPlace> stopPlaces = createStopPlaces(scheduledFlight);
        List<ScheduledStopPoint> scheduledStopPoints = createScheduledStopPoints(scheduledFlight);
        List<RoutePoint> routePoints = createRoutePoints(scheduledStopPoints, flightId);
        List<PassengerStopAssignment> stopAssignments = createStopAssignments(scheduledStopPoints, flightId);

        Route route = createRoute(routePoints, flightId, routePath);
        Operator operator = resolveOperatorFromIATA(scheduledFlight.getAirlineIATA());
        Line line = createLine(route, flightId, routePath, operator);
        JourneyPattern journeyPattern = createJourneyPattern(flightId, route, scheduledStopPoints);

        List<DayType> dayTypes = createDayTypes(scheduledFlight.getWeekDaysPattern(), flightId);
        List<ServiceJourney> serviceJourneys = createServiceJourneyList(scheduledFlight, dayTypes, journeyPattern, line);

        Frames_RelStructure frames = objectFactory().createFrames_RelStructure();
        frames.getCommonFrame().add(createResourceFrame(operator));
        frames.getCommonFrame().add(createSiteFrame(stopPlaces));

        JAXBElement<ServiceFrame> serviceFrame = createServiceFrame(
                publicationTimestamp,
                operator.getId(),
                scheduledFlight.getAirlineName(),
                scheduledFlight.getAirlineIATA(),
                flightId,
                routePoints,
                route,
                line,
                scheduledStopPoints,
                journeyPattern,
                stopAssignments
        );
        frames.getCommonFrame().add(serviceFrame);
        //frames.getCommonFrame().add(createServiceFrame(publicationTimestamp, operator.getId(), scheduledFlight.getAirlineName(), flightId, routePoints, route, line, scheduledStopPoints, journeyPattern, stopAssignments));

        frames.getCommonFrame().add(createTimetableFrame(scheduledFlight.getAvailabilityPeriod(), serviceJourneys));
        frames.getCommonFrame().add(createServiceCalendarFrame(dayTypes));

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
                .withVersion("1.0")
                .withPublicationTimestamp(publicationTimestamp)
                .withParticipantRef(avinorDataSet.getName())
                .withDescription(createMultilingualString(String.format("Flight %s : %s", flightId, routePath)))
                .withDataObjects(dataObjects);
    }

    public JAXBElement<CompositeFrame> createCompositeFrame(OffsetDateTime publicationTimestamp, String flightId, Frames_RelStructure frames) {
        Codespaces_RelStructure codespaces = objectFactory().createCodespaces_RelStructure()
                .withCodespaceRefOrCodespace(Arrays.asList(avinorCodespace(), nsrCodespace()));

/*
        CodespaceRefStructure codespaceRefStructure = objectFactory().createCodespaceRefStructure()
                .withRef(avinorCodespace().getId());
*/

        LocaleStructure localeStructure = objectFactory().createLocaleStructure()
                .withTimeZone("UTC")  // TODO extract UTC string to common constant
                .withDefaultLanguage("no");

        VersionFrameDefaultsStructure versionFrameDefaultsStructure = objectFactory().createVersionFrameDefaultsStructure()
                //.withDefaultCodespaceRef(codespaceRefStructure)
                .withDefaultLocale(localeStructure);

        CompositeFrame compositeFrame = objectFactory().createCompositeFrame()
                .withVersion(VERSION_ONE)
                .withCreated(publicationTimestamp)
                .withId(String.format("%s:CompositeFrame:%s", AVINOR_AUTHORITY_ID, flightId))
                .withCodespaces(codespaces)
                .withFrameDefaults(versionFrameDefaultsStructure)
                .withFrames(frames);

        return objectFactory().createCompositeFrame(compositeFrame);
    }

    public JAXBElement<ResourceFrame> createResourceFrame(Operator operator) {
        OrganisationsInFrame_RelStructure organisationsInFrame = objectFactory().createOrganisationsInFrame_RelStructure();
        organisationsInFrame.getOrganisation_().add(objectFactory().createAuthority(createAuthority()));
        organisationsInFrame.getOrganisation_().add(objectFactory().createOperator(operator));

        ResourceFrame resourceFrame = objectFactory().createResourceFrame()
                .withVersion(VERSION_ONE)
                .withId(String.format("%s:ResourceFrame:1", AVINOR_AUTHORITY_ID)) // TODO: generate id
                .withOrganisations(organisationsInFrame);

        return objectFactory().createResourceFrame(resourceFrame);
    }

    public JAXBElement<SiteFrame> createSiteFrame(List<StopPlace> stopPlaces) {
        StopPlacesInFrame_RelStructure stopPlacesInFrameRelStructure = objectFactory().createStopPlacesInFrame_RelStructure()
                        .withStopPlace(stopPlaces);
        SiteFrame siteFrame = objectFactory().createSiteFrame()
                .withVersion(VERSION_ONE)
                .withId(String.format("%s:SiteFrame:1", AVINOR_AUTHORITY_ID))
                .withStopPlaces(stopPlacesInFrameRelStructure);
        return objectFactory().createSiteFrame(siteFrame);
    }

    // TODO consider making line argument/parameter varargs instead (a Network can have multiple lines)
    public JAXBElement<ServiceFrame> createServiceFrame(OffsetDateTime publicationTimestamp, String organisationId,
            String airlineName, String airlineIata, String flightId, List<RoutePoint> routePoints, Route route, Line line,
            List<ScheduledStopPoint> scheduledStopPoints, JourneyPattern journeyPattern, List<PassengerStopAssignment> stopAssignments) {

/*
        OrganisationRefStructure organisationRefStructure = objectFactory().createOrganisationRefStructure()
                .withRef(organisationId);
        JAXBElement<OrganisationRefStructure> organisationRefStructElement = objectFactory().createTransportOrganisationRef(organisationRefStructure);

        LineRefStructure lineRefStructure = objectFactory().createLineRefStructure()
                .withVersion(VERSION_ONE)
                .withValue(line.getId())
                .withRef(line.getId());

        JAXBElement<LineRefStructure> lineRefStructElement = objectFactory().createLineRef(lineRefStructure);

        @SuppressWarnings("unchecked")
        LineRefs_RelStructure lineRefRelsStruct = objectFactory().createLineRefs_RelStructure()
                .withLineRef(lineRefStructElement);

        GroupOfLines groupOfLines = objectFactory().createGroupOfLines()
                .withMembers(lineRefRelsStruct)
                .withId("AVI:GroupOfLines:1")
                .withVersion(VERSION_ONE)
                .withName(createMultilingualString("Operator:GroupOfLines:1"));

        GroupsOfLinesInFrame_RelStructure groupsOfLinesInFrameStruct = objectFactory().createGroupsOfLinesInFrame_RelStructure()
                .withGroupOfLines(groupOfLines);
*/

        String networkId = NetexObjectIdCreator.createNetworkId(AVINOR_AUTHORITY_ID, airlineIata);

        Network network = objectFactory().createNetwork()
                .withVersion(VERSION_ONE)
                .withChanged(publicationTimestamp)
                .withId(networkId)
                .withName(createMultilingualString(airlineName));
                //.withTransportOrganisationRef(organisationRefStructElement); // schema validation requires 'abstract' attribute set to false (not available in netex model)
                //.withGroupsOfLines(groupsOfLinesInFrameStruct);

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

        ServiceFrame serviceFrame = objectFactory().createServiceFrame()
                .withVersion(VERSION_ONE)
                .withId(String.format("%s:ServiceFrame:%s", AVINOR_AUTHORITY_ID, flightId))
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
        ServiceCalendarFrame serviceCalendarFrame = objectFactory().createServiceCalendarFrame()
                .withVersion(VERSION_ONE)
                .withId(String.format("%s:ServiceCalendarFrame:1", AVINOR_AUTHORITY_ID))
                .withDayTypes(dayTypesStructure);
        return objectFactory().createServiceCalendarFrame(serviceCalendarFrame);
    }

    private List<DayType> createDayTypes(Set<DayOfWeek> weekDaysPattern, String flightId) {
        Map<Boolean, List<DayOfWeek>> dayOfWeeksByDayType = weekDaysPattern.stream()
                .collect(Collectors.partitioningBy(dayOfWeek -> dayOfWeek.query(DateUtils.WorkDays::isWorkDay)));
        List<DayOfWeek> workDays = dayOfWeeksByDayType.get(Boolean.TRUE);
        List<DayOfWeek> weekendDays = dayOfWeeksByDayType.get(Boolean.FALSE);
        List<DayType> dayTypes = Lists.newArrayList();
        if (!workDays.isEmpty()) {
            dayTypes.add(createDayType(workDays, flightId, true));
        }
        if (!weekendDays.isEmpty()) {
            dayTypes.add(createDayType(weekendDays, flightId, false));
        }
        return dayTypes;
    }

    public DayType createDayType(List<DayOfWeek> daysOfWeekPattern, String flightId, boolean isWorkDays) {
        List<DayOfWeekEnumeration> daysOfWeek = Lists.newArrayList();
        daysOfWeekPattern.forEach(dayOfWeek -> daysOfWeek.add(dayOfWeekMap.get(dayOfWeek)));
        PropertyOfDay propertyOfDayWeekDays = objectFactory().createPropertyOfDay();
        propertyOfDayWeekDays.getDaysOfWeek().addAll(daysOfWeek);
        PropertiesOfDay_RelStructure propertiesOfDay = objectFactory().createPropertiesOfDay_RelStructure()
                .withPropertyOfDay(propertyOfDayWeekDays);
        return objectFactory().createDayType()
                .withVersion(VERSION_ONE)
                .withId(String.format("%s:DayType:%s", flightId, isWorkDays ? "weekday" : "weekend"))
                .withName(createMultilingualString(isWorkDays ? WORK_DAYS_DISPLAY_NAME : WEEKEND_DAYS_DISPLAY_NAME))
                .withProperties(propertiesOfDay);
    }

    public JAXBElement<TimetableFrame> createTimetableFrame(AvailabilityPeriod availabilityPeriod, List<ServiceJourney> serviceJourneys) {
        ValidityConditions_RelStructure validityConditionsRelStructure = objectFactory().createValidityConditions_RelStructure()
                .withValidityConditionRefOrValidBetweenOrValidityCondition_(createAvailabilityCondition(availabilityPeriod));
        JourneysInFrame_RelStructure journeysInFrameRelStructure = objectFactory().createJourneysInFrame_RelStructure();
        journeysInFrameRelStructure.getDatedServiceJourneyOrDeadRunOrServiceJourney().addAll(serviceJourneys);
        TimetableFrame timetableFrame = objectFactory().createTimetableFrame()
                .withVersion(VERSION_ONE)
                .withId(String.format("%s:TimetableFrame:1", AVINOR_AUTHORITY_ID))
                .withValidityConditions(validityConditionsRelStructure)
                .withVehicleJourneys(journeysInFrameRelStructure);
        return objectFactory().createTimetableFrame(timetableFrame);
    }

    public List<StopPlace> createStopPlaces(ScheduledFlight scheduledFlight) throws Exception {
        Map<String, StopPlaceDataSet> stopPlaceDataSets = netexStaticDataSet.getStopPlaces();

        if (scheduledFlight instanceof ScheduledDirectFlight) {
            String departureAirportIATA = scheduledFlight.getDepartureAirportIATA();
            String arrivalAirportIATA = scheduledFlight.getArrivalAirportIATA();
            StopPlaceDataSet departureStopPlaceDataSet = stopPlaceDataSets.get(departureAirportIATA.toLowerCase());
            StopPlaceDataSet arrivalStopPlaceDataSet = stopPlaceDataSets.get(arrivalAirportIATA.toLowerCase());

            String departureStopPlaceId = stopPlaceIdCache.computeIfAbsent(departureAirportIATA,
                    iataCode -> NetexObjectIdCreator.createStopPlaceId(AVINOR_AUTHORITY_ID, iataCode));

            String arrivalStopPlaceId = stopPlaceIdCache.computeIfAbsent(scheduledFlight.getArrivalAirportIATA(),
                    iataCode -> NetexObjectIdCreator.createStopPlaceId(AVINOR_AUTHORITY_ID, iataCode));

            StopPlace departureStopPlace = createStopPlace(departureStopPlaceId, departureAirportIATA, departureStopPlaceDataSet);
            StopPlace arrivalStopPlace = createStopPlace(arrivalStopPlaceId, arrivalAirportIATA, arrivalStopPlaceDataSet);

            return Lists.newArrayList(departureStopPlace, arrivalStopPlace);

        } else if (scheduledFlight instanceof ScheduledStopoverFlight) {
            List<ScheduledStopover> scheduledStopovers = ((ScheduledStopoverFlight) scheduledFlight).getScheduledStopovers();
            List<StopPlace> stopPlaces = new ArrayList<>(scheduledStopovers.size());

            Set<String> iataCodes = scheduledStopovers.stream()
                    .map(ScheduledStopover::getAirportIATA)
                    .collect(Collectors.toSet());

            for (String iata : iataCodes) {
                String stopPlaceId = stopPlaceIdCache.computeIfAbsent(iata,
                        iataCode -> NetexObjectIdCreator.createStopPlaceId(AVINOR_AUTHORITY_ID, iataCode));

                StopPlaceDataSet stopPlaceDataSet = stopPlaceDataSets.get(iata.toLowerCase());
                StopPlace stopPlace = createStopPlace(stopPlaceId, iata, stopPlaceDataSet);
                stopPlaces.add(stopPlace);
            }

            return stopPlaces;

        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }
    }

    private StopPlace createStopPlace(String id, String airportIATA, StopPlaceDataSet stopPlaceDataSet) {
        LocationStructure locationStruct = objectFactory().createLocationStructure()
                .withSrsName("WGS84")
                .withLatitude(stopPlaceDataSet.getLocation().getLatitude())
                .withLongitude(stopPlaceDataSet.getLocation().getLongitude());

        SimplePoint_VersionStructure pointStruct = objectFactory().createSimplePoint_VersionStructure()
                .withLocation(locationStruct);

        return objectFactory().createStopPlace()
                .withVersion(VERSION_ONE)
                .withId(id)
                .withName(createMultilingualString(stopPlaceDataSet.getName()))
                .withShortName(createMultilingualString(airportIATA))
                .withCentroid(pointStruct)
                //.withTransportMode(VehicleModeEnumeration.AIR)
                .withStopPlaceType(StopTypeEnumeration.AIRPORT);
    }

    public JAXBElement<AvailabilityCondition> createAvailabilityCondition(AvailabilityPeriod availabilityPeriod) {
        AvailabilityCondition availabilityCondition = objectFactory().createAvailabilityCondition()
                .withVersion(VERSION_ONE)
                .withId(String.format("%s:AvailabilityCondition:1", AVINOR_AUTHORITY_ID))
                .withFromDate(availabilityPeriod.getPeriodFromDateTime())
                .withToDate(availabilityPeriod.getPeriodToDateTime());
        return objectFactory().createAvailabilityCondition(availabilityCondition);
    }

    public List<ServiceJourney> createServiceJourneyList(ScheduledFlight scheduledFlight, List<DayType> dayTypes,
            JourneyPattern journeyPattern, Line line) throws IllegalArgumentException {

        List<ServiceJourney> serviceJourneyList = new ArrayList<>();
        TimetabledPassingTimes_RelStructure passingTimesRelStructure = objectFactory().createTimetabledPassingTimes_RelStructure();

        PointsInJourneyPattern_RelStructure pointsInSequence = journeyPattern.getPointsInSequence();
        List<PointInLinkSequence_VersionedChildStructure> pointsInLinkSequence = pointsInSequence.getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern();

        JourneyPatternRefStructure journeyPatternRefStructure = objectFactory().createJourneyPatternRefStructure()
                .withRef(journeyPattern.getId());
        JAXBElement<JourneyPatternRefStructure> journeyPatternRefStructureElement = objectFactory().createJourneyPatternRef(journeyPatternRefStructure);

        DayTypeRefs_RelStructure dayTypeStructure = objectFactory().createDayTypeRefs_RelStructure();

        dayTypes.forEach(dayType -> {
            DayTypeRefStructure dayTypeRef = objectFactory().createDayTypeRefStructure().withRef(dayType.getId());
            dayTypeStructure.getDayTypeRef().add(objectFactory().createDayTypeRef(dayTypeRef));
        });

        if (scheduledFlight instanceof ScheduledDirectFlight) {
            StopPointInJourneyPatternRefStructure departureStopPointInJourneyPattern = objectFactory().createStopPointInJourneyPatternRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(pointsInLinkSequence.get(0).getId());

            TimetabledPassingTime departurePassingTime = objectFactory().createTimetabledPassingTime()
                    .withPointInJourneyPatternRef(objectFactory().createStopPointInJourneyPatternRef(departureStopPointInJourneyPattern))
                    .withDepartureTime(scheduledFlight.getTimeOfDeparture());
            passingTimesRelStructure.withTimetabledPassingTime(departurePassingTime);

            StopPointInJourneyPatternRefStructure arrivalStopPointInJourneyPattern = objectFactory().createStopPointInJourneyPatternRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(pointsInLinkSequence.get(1).getId());

            TimetabledPassingTime arrivalPassingTime = objectFactory().createTimetabledPassingTime()
                    .withPointInJourneyPatternRef(objectFactory().createStopPointInJourneyPatternRef(arrivalStopPointInJourneyPattern))
                    .withArrivalTime(scheduledFlight.getTimeOfArrival());
            passingTimesRelStructure.withTimetabledPassingTime(arrivalPassingTime);

            LineRefStructure lineRefStruct = objectFactory().createLineRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(line.getId());
            JAXBElement<LineRefStructure> lineRefStructElement = objectFactory().createLineRef(lineRefStruct);

            ServiceJourney serviceJourney = objectFactory().createServiceJourney()
                    .withVersion(VERSION_ONE)
                    .withId(String.format("%s:ServiceJourney:%s", AVINOR_AUTHORITY_ID, scheduledFlight.getAirlineFlightId()))
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
            Iterator<ScheduledStopover> stopoversiterator = scheduledStopovers.iterator();
            Iterator<PointInLinkSequence_VersionedChildStructure> pointInLinkSequenceIterator = pointsInLinkSequence.iterator();

            while (stopoversiterator.hasNext() && pointInLinkSequenceIterator.hasNext()) {
                ScheduledStopover scheduledStopover = stopoversiterator.next();
                PointInLinkSequence_VersionedChildStructure pointInLinkSequence = pointInLinkSequenceIterator.next();

                StopPointInJourneyPatternRefStructure stopPointInJourneyPatternRef = objectFactory().createStopPointInJourneyPatternRefStructure()
                        .withVersion(VERSION_ONE)
                        .withRef(pointInLinkSequence.getId());

                TimetabledPassingTime passingTime = objectFactory().createTimetabledPassingTime()
                        .withPointInJourneyPatternRef(objectFactory().createStopPointInJourneyPatternRef(stopPointInJourneyPatternRef));

                if (scheduledStopover.getArrivalTime() != null) {
                    passingTime.setArrivalTime(scheduledStopover.getArrivalTime());
                }
                if (scheduledStopover.getDepartureTime() != null) {
                    passingTime.setDepartureTime(scheduledStopover.getDepartureTime());
                }

                passingTimesRelStructure.withTimetabledPassingTime(passingTime);
            }

            LineRefStructure lineRefStruct = objectFactory().createLineRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(line.getId());
            JAXBElement<LineRefStructure> lineRefStructElement = objectFactory().createLineRef(lineRefStruct);

            ServiceJourney serviceJourney = objectFactory().createServiceJourney()
                    .withVersion(VERSION_ONE)
                    .withId(String.format("%s:ServiceJourney:%s", AVINOR_AUTHORITY_ID, scheduledFlight.getAirlineFlightId()))
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

    // TODO use this, and consider moving to object fixture or factory
    // TODO consider simpler parameters as input, and no internal jaxb structures
    // TODO send in line id directly and not line
    private ServiceJourney createServiceJourney(String id, String publicCode, OffsetTime departureTime,
            DayTypeRefs_RelStructure dayTypeStructure, JAXBElement<JourneyPatternRefStructure> journeyPatternRefStructureElement,
            Line line, TimetabledPassingTimes_RelStructure passingTimesRelStructure) {

        LineRefStructure lineRefStruct = objectFactory().createLineRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(line.getId());
        JAXBElement<LineRefStructure> lineRefStructElement = objectFactory().createLineRef(lineRefStruct);

        return objectFactory().createServiceJourney()
                .withVersion(VERSION_ONE)
                .withId(id)
                .withPublicCode(publicCode)
                .withDepartureTime(departureTime)
                .withDayTypes(dayTypeStructure)
                .withJourneyPatternRef(journeyPatternRefStructureElement)
                .withLineRef(lineRefStructElement)
                .withPassingTimes(passingTimesRelStructure);
    }

    public List<RoutePoint> createRoutePoints(List<ScheduledStopPoint> scheduledStopPoints, String flightId) {
        List<RoutePoint> routePoints = new ArrayList<>();
        int[] idx = {1};
        scheduledStopPoints.forEach(stopPoint -> {
            PointRefStructure pointRefStructure = objectFactory().createPointRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(stopPoint.getId());
            PointProjection pointProjection = objectFactory().createPointProjection()
                    .withVersion(VERSION_ONE)
                    .withId(String.format("%s:PointProjection:%s101A0A006110100%d", AVINOR_AUTHORITY_ID, flightId, idx[0])) // @todo: generate postfix id in a serie
                    .withProjectedPointRef(pointRefStructure);
            Projections_RelStructure projections = objectFactory().createProjections_RelStructure()
                    .withProjectionRefOrProjection(objectFactory().createPointProjection(pointProjection));
            RoutePoint routePoint = objectFactory().createRoutePoint()
                    .withVersion(VERSION_ONE)
                    .withId(String.format("%s:RoutePoint:%s101A0A006110100%d", AVINOR_AUTHORITY_ID, flightId, idx[0])) // @todo: generate postfix id in a serie
                    .withProjections(projections);
            routePoints.add(routePoint);
            idx[0]++;
        });
        return routePoints;
    }

    public Route createRoute(List<RoutePoint> routePoints, String flightId, String routePath) {
        PointsOnRoute_RelStructure pointsOnRoute = objectFactory().createPointsOnRoute_RelStructure();

        int[] idx = {1};
        routePoints.forEach(routePoint -> {
            RoutePointRefStructure routePointReference = objectFactory().createRoutePointRefStructure()
                    .withVersion(VERSION_ONE) 
                    .withRef(routePoint.getId());
            PointOnRoute pointOnRoute = objectFactory().createPointOnRoute()
                    .withVersion(VERSION_ONE)
                    // @todo: fix this id, and remove the dash '-'
                    .withId(String.format("%s:PointOnRoute:%s101001%d", AVINOR_AUTHORITY_ID, flightId, idx[0])) // @todo: fix generation of serial numbers
                    .withPointRef(objectFactory().createRoutePointRef(routePointReference));
            pointsOnRoute.getPointOnRoute().add(pointOnRoute);
            idx[0]++;
        });

        //NetexObjectIdCreator.createRouteId(AVINOR_AUTHORITY_ID, "routeId"); // TODO use this instead
        return objectFactory().createRoute()
                .withVersion(VERSION_ONE)
                .withId(String.format("%s:Route:%s101", AVINOR_AUTHORITY_ID, flightId))
                .withName(createMultilingualString(routePath))
                .withPointsInSequence(pointsOnRoute);
    }

    // TODO consider moving to object fixture or factory class
    private Line createLine(Route route, String flightId, String routePath, Operator operator) {
        RouteRefStructure routeRefStructure = objectFactory().createRouteRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(route.getId());
        RouteRefs_RelStructure routeRefs = objectFactory().createRouteRefs_RelStructure()
                .withRouteRef(routeRefStructure);
        OperatorRefStructure operatorRefStructure = objectFactory().createOperatorRefStructure()
                .withRef(operator.getId());
        return objectFactory().createLine()
                .withVersion(VERSION_ONE)
                .withId(String.format("%s:Line:%s", AVINOR_AUTHORITY_ID, flightId))
                .withName(createMultilingualString(routePath))
                .withTransportMode(AllVehicleModesOfTransportEnumeration.AIR)
                .withPublicCode(flightId)
                .withOperatorRef(operatorRefStructure)
                .withRoutes(routeRefs);
    }

    private List<ScheduledStopPoint> createScheduledStopPoints(ScheduledFlight scheduledFlight) throws IllegalArgumentException {
        if (scheduledFlight instanceof ScheduledDirectFlight) {

            ScheduledStopPoint scheduledDepartureStopPoint = objectFactory().createScheduledStopPoint()
                    .withVersion(VERSION_ONE)
                    .withId(String.format("%s:ScheduledStopPoint:%s101001", AVINOR_AUTHORITY_ID, scheduledFlight.getAirlineFlightId()))
                    .withName(createMultilingualString(scheduledFlight.getDepartureAirportName()))
                    .withShortName(createMultilingualString(scheduledFlight.getDepartureAirportIATA()));

            ScheduledStopPoint scheduledArrivalStopPoint = objectFactory().createScheduledStopPoint()
                    .withVersion(VERSION_ONE)
                    .withId(String.format("%s:ScheduledStopPoint:%s101002", AVINOR_AUTHORITY_ID, scheduledFlight.getAirlineFlightId()))
                    .withName(createMultilingualString(scheduledFlight.getArrivalAirportName()))
                    .withShortName(createMultilingualString(scheduledFlight.getArrivalAirportIATA()));

            return Lists.newArrayList(scheduledDepartureStopPoint, scheduledArrivalStopPoint);

        } else if (scheduledFlight instanceof ScheduledStopoverFlight) {
            List<ScheduledStopover> scheduledStopovers = ((ScheduledStopoverFlight) scheduledFlight).getScheduledStopovers();
            List<ScheduledStopPoint> scheduledStopPoints = new ArrayList<>(scheduledStopovers.size());

/*
            for (ScheduledStopover scheduledStopover : scheduledStopovers) {

            }

*/
            int[] idx = {1};
            scheduledStopovers.forEach(stopover -> {
                ScheduledStopPoint scheduledStopPoint = objectFactory().createScheduledStopPoint()
                        .withVersion(VERSION_ONE)
                        .withId(String.format("%s:ScheduledStopPoint:%s10100%d", AVINOR_AUTHORITY_ID, scheduledFlight.getAirlineFlightId(), idx[0]))
                        .withName(createMultilingualString(stopover.getAirportName()))
                        .withShortName(createMultilingualString(stopover.getAirportIATA()));
                scheduledStopPoints.add(scheduledStopPoint);
                idx[0]++;
            });

            return scheduledStopPoints;
        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }
    }

    public JourneyPattern createJourneyPattern(String flightId, Route route, List<ScheduledStopPoint> scheduledStopPoints) {
        PointsInJourneyPattern_RelStructure pointsInJourneyPattern = objectFactory().createPointsInJourneyPattern_RelStructure();
        int[] idx = {1};
        scheduledStopPoints.forEach(stopPoint -> {
            ScheduledStopPointRefStructure scheduledStopPointRefStructure = objectFactory().createScheduledStopPointRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(String.format("%s:ScheduledStopPoint:%s10100%d", AVINOR_AUTHORITY_ID, flightId, idx[0])); // @todo: fix id generator
            StopPointInJourneyPattern stopPointInJourneyPattern = objectFactory().createStopPointInJourneyPattern()
                    .withVersion(VERSION_ONE)
                    .withId(String.format("%s:StopPointInJourneyPattern:%s10100%d", AVINOR_AUTHORITY_ID, flightId, idx[0])) // @todo: fix some id generator-counter here...
                    .withOrder(new BigInteger(Integer.toString(idx[0])))
                    .withScheduledStopPointRef(objectFactory().createScheduledStopPointRef(scheduledStopPointRefStructure));
            if (idx[0] == 1) {
                stopPointInJourneyPattern.setForAlighting(false);
            }
            if (idx[0] == scheduledStopPoints.size()) {
                stopPointInJourneyPattern.setForBoarding(false);
            }
            pointsInJourneyPattern.getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern().add(stopPointInJourneyPattern);
            idx[0]++;
        });
        RouteRefStructure routeRefStructure = objectFactory().createRouteRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(route.getId());
        return objectFactory().createJourneyPattern()
                .withVersion(VERSION_ONE)
                .withId(String.format("%s:JourneyPattern:%s101", AVINOR_AUTHORITY_ID, flightId)) // @todo: fix id generator
                .withRouteRef(routeRefStructure)
                .withPointsInSequence(pointsInJourneyPattern);
    }

    public List<PassengerStopAssignment> createStopAssignments(List<ScheduledStopPoint> scheduledStopPoints, String flightId) {
        List<PassengerStopAssignment> stopAssignments = new ArrayList<>(scheduledStopPoints.size());

        int index = 1;
        for (ScheduledStopPoint scheduledStopPoint : scheduledStopPoints) {
            ScheduledStopPointRefStructure scheduledStopPointRef = objectFactory().createScheduledStopPointRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(scheduledStopPoint.getId());

            String stopPlaceId = stopPlaceIdCache.get(scheduledStopPoint.getShortName().getValue());
            StopPlaceRefStructure stopPlaceRef = objectFactory().createStopPlaceRefStructure()
                    //.withVersion(VERSION_ONE)
                    .withRef(stopPlaceId);
            

            PassengerStopAssignment passengerStopAssignment = objectFactory().createPassengerStopAssignment()
                    .withVersion(VERSION_ONE)
                    .withOrder(new BigInteger(Integer.toString(index)))
                    .withId(String.format("%s:PassengerStopAssignment:%s10100%d", AVINOR_AUTHORITY_ID, flightId, index)) // @todo: fix the id generation
                    .withScheduledStopPointRef(scheduledStopPointRef)
                    .withStopPlaceRef(stopPlaceRef);

            stopAssignments.add(passengerStopAssignment);
            index++;
        }

        return stopAssignments;
    }

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

    @Bean
    public ObjectFactory objectFactory() {
        return new ObjectFactory();
    }

    public Codespace avinorCodespace() {
        OrganisationDataSet avinorDataSet = netexStaticDataSet.getOrganisations().get(AVINOR_AUTHORITY_ID.toLowerCase());

        return objectFactory().createCodespace()
                .withId(avinorDataSet.getName().toLowerCase())
                .withXmlns(AVINOR_AUTHORITY_ID)
                .withXmlnsUrl(avinorDataSet.getUrl());
    }

    public Codespace nsrCodespace() {
        OrganisationDataSet nsrDataSet = netexStaticDataSet.getOrganisations().get(NSR_AUTHORITY_ID.toLowerCase());

        return objectFactory().createCodespace()
                .withId(NSR_AUTHORITY_ID.toLowerCase())
                .withXmlns(NSR_AUTHORITY_ID)
                .withXmlnsUrl(nsrDataSet.getUrl());
    }

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

    public JAXBElement<ContactStructure> createContactStructure(String phone, String url) {
        ContactStructure contactStructure = objectFactory().createContactStructure()
                .withPhone(phone)
                .withUrl(url);

        return objectFactory().createOrganisation_VersionStructureContactDetails(contactStructure);
    }

}
