package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.config.*;
import no.rutebanken.extime.model.*;
import no.rutebanken.extime.util.DateUtils;
import no.rutebanken.netex.model.*;
import no.rutebanken.netex.model.PublicationDeliveryStructure.DataObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Component(value = "scheduledFlightToNetexConverter")
public class ScheduledFlightToNetexConverter {

    private static final HashMap<DayOfWeek, DayOfWeekEnumeration> DAY_OF_WEEK_MAP = new HashMap<>();
    private static final String WORK_DAYS_DISPLAY_NAME = "Ukedager (mandag til fredag)";
    private static final String WEEKEND_DAYS_DISPLAY_NAME = "Helgdager (lørdag og søndag)";

    static {
        DAY_OF_WEEK_MAP.put(DayOfWeek.MONDAY, DayOfWeekEnumeration.MONDAY);
        DAY_OF_WEEK_MAP.put(DayOfWeek.TUESDAY, DayOfWeekEnumeration.TUESDAY);
        DAY_OF_WEEK_MAP.put(DayOfWeek.WEDNESDAY, DayOfWeekEnumeration.WEDNESDAY);
        DAY_OF_WEEK_MAP.put(DayOfWeek.THURSDAY, DayOfWeekEnumeration.THURSDAY);
        DAY_OF_WEEK_MAP.put(DayOfWeek.FRIDAY, DayOfWeekEnumeration.FRIDAY);
        DAY_OF_WEEK_MAP.put(DayOfWeek.SATURDAY, DayOfWeekEnumeration.SATURDAY);
        DAY_OF_WEEK_MAP.put(DayOfWeek.SUNDAY, DayOfWeekEnumeration.SUNDAY);
    }

    private AvinorAuthorityConfig avinorConfig;
    private NhrAuthorityConfig nhrConfig;
    private SasOperatorConfig sasConfig;
    private WideroeOperatorConfig wideroeConfig;
    private NorwegianOperatorConfig norwegianConfig;

    public JAXBElement<PublicationDeliveryStructure> convertToNetex(ScheduledFlight scheduledFlight) {
        OffsetDateTime publicationTimestamp = OffsetDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"));
        LocalDate dateOfOperation = scheduledFlight.getDateOfOperation();
        String routePath = String.format("%s-%s", scheduledFlight.getDepartureAirportName(), scheduledFlight.getArrivalAirportName());
        String flightId = scheduledFlight.getAirlineFlightId();

        List<StopPlace> stopPlaces = createStopPlaces(scheduledFlight);
        List<ScheduledStopPoint> scheduledStopPoints = createScheduledStopPoints(scheduledFlight);
        List<RoutePoint> routePoints = createRoutePoints(scheduledStopPoints, flightId);
        List<PassengerStopAssignment> stopAssignments = createStopAssignments(scheduledStopPoints, stopPlaces, flightId);

        Route route = createRoute(routePoints, flightId, routePath);
        Operator operator = resolveOperatorFromIATA(scheduledFlight.getAirlineIATA());
        Line line = createLine(route, flightId, routePath, operator);
        JourneyPattern journeyPattern = createJourneyPattern(flightId, route, scheduledStopPoints);

        List<DayType> dayTypes = createDayTypes(scheduledFlight.getWeekDaysPattern(), flightId);
        List<ServiceJourney> serviceJourneys = createServiceJourneyList(scheduledFlight, dayTypes, journeyPattern, line);

        Frames_RelStructure frames = objectFactory().createFrames_RelStructure();
        frames.getCommonFrame().add(createResourceFrame(operator));
        frames.getCommonFrame().add(createSiteFrame(stopPlaces));
        frames.getCommonFrame().add(createServiceFrame(publicationTimestamp, scheduledFlight.getAirlineName(), flightId, routePoints,
                route, line, scheduledStopPoints, journeyPattern, stopAssignments));
        frames.getCommonFrame().add(createTimetableFrame(scheduledFlight.getAvailabilityPeriod(), serviceJourneys));
        frames.getCommonFrame().add(createServiceCalendarFrame(dayTypes));

        JAXBElement<CompositeFrame> compositeFrame = createCompositeFrame(publicationTimestamp, flightId, frames);
        PublicationDeliveryStructure publicationDeliveryStructure = createPublicationDeliveryStructure(publicationTimestamp, compositeFrame, flightId, routePath);
        return objectFactory().createPublicationDelivery(publicationDeliveryStructure);
    }

    public PublicationDeliveryStructure createPublicationDeliveryStructure(OffsetDateTime publicationTimestamp,
                                                                           JAXBElement<CompositeFrame> compositeFrame,
                                                                           String flightId, String routePath) {
        DataObjects dataObjects = objectFactory().createPublicationDeliveryStructureDataObjects();
        dataObjects.getCompositeFrameOrCommonFrame().add(compositeFrame);
        return objectFactory().createPublicationDeliveryStructure()
                .withVersion("1.0")
                .withPublicationTimestamp(publicationTimestamp)
                .withParticipantRef(getNhrConfig().getId())
                .withDescription(createMultilingualString(String.format("Flight %s : %s", flightId, routePath)))
                .withDataObjects(dataObjects);
    }

    public JAXBElement<CompositeFrame> createCompositeFrame(OffsetDateTime publicationTimestamp, String flightId, Frames_RelStructure frames) {
        Codespaces_RelStructure codespaces = objectFactory().createCodespaces_RelStructure()
                .withCodespaceRefOrCodespace(Arrays.asList(avinorCodespace(), nhrCodespace()));
        CodespaceRefStructure codespaceRefStructure = objectFactory().createCodespaceRefStructure()
                .withRef(avinorCodespace().getId());
        LocaleStructure localeStructure = objectFactory().createLocaleStructure()
                .withTimeZone("UTC")
                .withDefaultLanguage("no");
        VersionFrameDefaultsStructure versionFrameDefaultsStructure = objectFactory().createVersionFrameDefaultsStructure()
                .withDefaultCodespaceRef(codespaceRefStructure)
                .withDefaultLocale(localeStructure);
        CompositeFrame compositeFrame = objectFactory().createCompositeFrame()
                .withVersion("1")
                .withCreated(publicationTimestamp)
                .withId(String.format("%s:CompositeFrame:%s", getAvinorConfig().getId(), flightId))
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
                .withVersion("any")
                .withId(String.format("%s:ResourceFrame:1", getAvinorConfig().getId()))
                .withOrganisations(organisationsInFrame);
        return objectFactory().createResourceFrame(resourceFrame);
    }

    public JAXBElement<SiteFrame> createSiteFrame(List<StopPlace> stopPlaces) {
        StopPlacesInFrame_RelStructure stopPlacesInFrameRelStructure = objectFactory().createStopPlacesInFrame_RelStructure()
                        .withStopPlace(stopPlaces);
        SiteFrame siteFrame = objectFactory().createSiteFrame()
                .withVersion("any")
                .withId(String.format("%s:SiteFrame:1", getAvinorConfig().getId()))
                .withStopPlaces(stopPlacesInFrameRelStructure);
        return objectFactory().createSiteFrame(siteFrame);
    }

    public JAXBElement<ServiceFrame> createServiceFrame(OffsetDateTime publicationTimestamp, String airlineName, String flightId, List<RoutePoint> routePoints,
                                                        Route route, Line line, List<ScheduledStopPoint> scheduledStopPoints,
                                                        JourneyPattern journeyPattern, List<PassengerStopAssignment> stopAssignments) {
        Network network = objectFactory().createNetwork()
                .withVersion("1")
                .withChanged(publicationTimestamp)
                .withId(String.format("%s:GroupOfLine:%s", getAvinorConfig().getId(), getAvinorConfig().getName()))
                .withName(createMultilingualString(airlineName));
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
                .withVersion("any")
                .withId(String.format("%s:ServiceFrame:%s", getAvinorConfig().getId(), flightId))
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
                .withVersion("1")
                .withId(String.format("%s:ServiceCalendarFrame:1", getAvinorConfig().getId()))
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
        daysOfWeekPattern.forEach(dayOfWeek -> daysOfWeek.add(DAY_OF_WEEK_MAP.get(dayOfWeek)));
        PropertyOfDay propertyOfDayWeekDays = objectFactory().createPropertyOfDay();
        propertyOfDayWeekDays.getDaysOfWeek().addAll(daysOfWeek);
        PropertiesOfDay_RelStructure propertiesOfDay = objectFactory().createPropertiesOfDay_RelStructure()
                .withPropertyOfDay(propertyOfDayWeekDays);
        return objectFactory().createDayType()
                .withVersion("any")
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
                .withVersion("any")
                .withId(String.format("%s:TimetableFrame:1", getAvinorConfig().getId()))
                .withValidityConditions(validityConditionsRelStructure)
                .withVehicleJourneys(journeysInFrameRelStructure);
        return objectFactory().createTimetableFrame(timetableFrame);
    }

    public List<StopPlace> createStopPlaces(ScheduledFlight scheduledFlight) throws IllegalArgumentException {
        if (scheduledFlight instanceof ScheduledDirectFlight) {
            StopPlace departureStopPlace = objectFactory().createStopPlace()
                    .withVersion("1")
                    .withId("NSR:StopPlace:03011521") // @todo: retrieve the actual stopplace id from NHR
                    .withName(createMultilingualString(scheduledFlight.getDepartureAirportName()))
                    .withShortName(createMultilingualString(scheduledFlight.getDepartureAirportIATA()))
                    .withTransportMode(VehicleModeEnumeration.AIR)
                    .withStopPlaceType(StopTypeEnumeration.AIRPORT);
            StopPlace arrivalStopPlace = objectFactory().createStopPlace()
                    .withVersion("1")
                    .withId("NSR:StopPlace:03011522")  // @todo: retrieve the actual stopplace id from NHR
                    .withName(createMultilingualString(scheduledFlight.getArrivalAirportName()))
                    .withShortName(createMultilingualString(scheduledFlight.getArrivalAirportIATA()))
                    .withTransportMode(VehicleModeEnumeration.AIR)
                    .withStopPlaceType(StopTypeEnumeration.AIRPORT);
            return Lists.newArrayList(departureStopPlace, arrivalStopPlace);
        } else if (scheduledFlight instanceof ScheduledStopoverFlight) {
            List<ScheduledStopover> scheduledStopovers = ((ScheduledStopoverFlight) scheduledFlight).getScheduledStopovers();
            List<StopPlace> stopPlaces = new ArrayList<>(scheduledStopovers.size());
            int[] idx = {1};
            scheduledStopovers.forEach(scheduledStopover -> {
                StopPlace stopPlace = objectFactory().createStopPlace()
                        .withVersion("1")
                        .withId(String.format("NSR:StopPlace:0301152%d", idx[0]))  // @todo: retrieve the actual stopplace id from NHR
                        .withName(createMultilingualString(scheduledStopover.getAirportName()))
                        .withShortName(createMultilingualString(scheduledStopover.getAirportIATA()))
                        .withTransportMode(VehicleModeEnumeration.AIR)
                        .withStopPlaceType(StopTypeEnumeration.AIRPORT);
                stopPlaces.add(stopPlace);
                idx[0]++;
            });
            return stopPlaces;
        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }
    }

    public JAXBElement<AvailabilityCondition> createAvailabilityCondition(AvailabilityPeriod availabilityPeriod) {
        AvailabilityCondition availabilityCondition = objectFactory().createAvailabilityCondition()
                .withVersion("any")
                .withId(String.format("%s:AvailabilityCondition:1", getAvinorConfig().getId()))
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
                    .withVersion("any")
                    .withRef(pointsInLinkSequence.get(0).getId());
            TimetabledPassingTime departurePassingTime = objectFactory().createTimetabledPassingTime()
                    .withPointInJourneyPatternRef(objectFactory().createStopPointInJourneyPatternRef(departureStopPointInJourneyPattern))
                    .withDepartureTime(scheduledFlight.getTimeOfDeparture());
            passingTimesRelStructure.withTimetabledPassingTime(departurePassingTime);
            StopPointInJourneyPatternRefStructure arrivalStopPointInJourneyPattern = objectFactory().createStopPointInJourneyPatternRefStructure()
                    .withVersion("any")
                    .withRef(pointsInLinkSequence.get(1).getId());
            TimetabledPassingTime arrivalPassingTime = objectFactory().createTimetabledPassingTime()
                    .withPointInJourneyPatternRef(objectFactory().createStopPointInJourneyPatternRef(arrivalStopPointInJourneyPattern))
                    .withArrivalTime(scheduledFlight.getTimeOfArrival());
            passingTimesRelStructure.withTimetabledPassingTime(arrivalPassingTime);
            ServiceJourney serviceJourney = objectFactory().createServiceJourney()
                    .withVersion("any")
                    .withId(String.format("%s:ServiceJourney:%s", getAvinorConfig().getId(), scheduledFlight.getAirlineFlightId()))
                    .withPublicCode(scheduledFlight.getAirlineFlightId())
                    .withDepartureTime(scheduledFlight.getTimeOfDeparture())
                    .withDayTypes(dayTypeStructure)
                    .withJourneyPatternRef(journeyPatternRefStructureElement)
                    .withLineRef(objectFactory().createLineRef(objectFactory().createLineRefStructure().withRef(line.getId())))
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
                        .withVersion("any")
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
            ServiceJourney serviceJourney = objectFactory().createServiceJourney()
                    .withVersion("any")
                    .withId(String.format("%s:ServiceJourney:%s", getAvinorConfig().getId(), scheduledFlight.getAirlineFlightId()))
                    .withPublicCode(scheduledFlight.getAirlineFlightId())
                    .withDepartureTime(scheduledStopovers.get(0).getDepartureTime())
                    .withDayTypes(dayTypeStructure)
                    .withJourneyPatternRef(journeyPatternRefStructureElement)
                    .withLineRef(objectFactory().createLineRef(objectFactory().createLineRefStructure().withRef(line.getId())))
                    .withPassingTimes(passingTimesRelStructure);
            serviceJourneyList.add(serviceJourney);
            return serviceJourneyList;
        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }
    }

    public List<RoutePoint> createRoutePoints(List<ScheduledStopPoint> scheduledStopPoints, String flightId) {
        List<RoutePoint> routePoints = new ArrayList<>();
        int[] idx = {1};
        scheduledStopPoints.forEach(stopPoint -> {
            PointRefStructure pointRefStructure = objectFactory().createPointRefStructure()
                    .withVersion("any")
                    .withRef(stopPoint.getId());
            PointProjection pointProjection = objectFactory().createPointProjection()
                    .withVersion("any")
                    .withId(String.format("%s:PointProjection:%s101A0A006110100%d", getAvinorConfig().getId(), flightId, idx[0])) // @todo: generate postfix id in a serie
                    .withProjectedPointRef(pointRefStructure);
            Projections_RelStructure projections = objectFactory().createProjections_RelStructure()
                    .withProjectionRefOrProjection(objectFactory().createPointProjection(pointProjection));
            RoutePoint routePoint = objectFactory().createRoutePoint()
                    .withVersion("any")
                    .withId(String.format("%s:RoutePoint:%s101A0A006110100%d", getAvinorConfig().getId(), flightId, idx[0])) // @todo: generate postfix id in a serie
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
                    //.withVersion("any") // @todo: temp. disable to prevent id check, enable and fix
                    .withRef(routePoint.getId());
            PointOnRoute pointOnRoute = objectFactory().createPointOnRoute()
                    .withVersion("any")
                    // @todo: fix this id, and remove the dash '-'
                    .withId(String.format("%s:PointOnRoute:%s101001%d", getAvinorConfig().getId(), flightId, idx[0])) // @todo: fix generation of serial numbers
                    .withPointRef(objectFactory().createRoutePointRef(routePointReference));
            pointsOnRoute.getPointOnRoute().add(pointOnRoute);
            idx[0]++;
        });
        return objectFactory().createRoute()
                .withVersion("any")
                .withId(String.format("%s:Route:%s101", getAvinorConfig().getId(), flightId))
                .withName(createMultilingualString(String.format("%s: %s", flightId, routePath)))
                .withPointsInSequence(pointsOnRoute);
    }

    private Line createLine(Route route, String flightId, String routePath, Operator operator) {
        RouteRefStructure routeRefStructure = objectFactory().createRouteRefStructure()
                .withVersion("any")
                .withRef(route.getId());
        RouteRefs_RelStructure routeRefs = objectFactory().createRouteRefs_RelStructure()
                .withRouteRef(routeRefStructure);
        OperatorRefStructure operatorRefStructure = objectFactory().createOperatorRefStructure()
                .withRef(operator.getId());
        return objectFactory().createLine()
                .withVersion("any")
                .withId(String.format("%s:Line:%s", getAvinorConfig().getId(), flightId))
                .withName(createMultilingualString(routePath))
                .withTransportMode(AllVehicleModesOfTransportEnumeration.AIR)
                .withPublicCode(flightId)
                .withOperatorRef(operatorRefStructure)
                .withRoutes(routeRefs);
    }

    private List<ScheduledStopPoint> createScheduledStopPoints(ScheduledFlight scheduledFlight) throws IllegalArgumentException {
        if (scheduledFlight instanceof ScheduledDirectFlight) {
            ScheduledStopPoint scheduledDepartureStopPoint = objectFactory().createScheduledStopPoint()
                    .withVersion("any")
                    .withId(String.format("%s:ScheduledStopPoint:%s101001", getAvinorConfig().getId(), scheduledFlight.getAirlineFlightId()))
                    .withName(createMultilingualString(scheduledFlight.getDepartureAirportName()));
            ScheduledStopPoint scheduledArrivalStopPoint = objectFactory().createScheduledStopPoint()
                    .withVersion("any")
                    .withId(String.format("%s:ScheduledStopPoint:%s101002", getAvinorConfig().getId(), scheduledFlight.getAirlineFlightId()))
                    .withName(createMultilingualString(scheduledFlight.getArrivalAirportName()));
            return Lists.newArrayList(scheduledDepartureStopPoint, scheduledArrivalStopPoint);
        } else if (scheduledFlight instanceof ScheduledStopoverFlight) {
            List<ScheduledStopover> scheduledStopovers = ((ScheduledStopoverFlight) scheduledFlight).getScheduledStopovers();
            List<ScheduledStopPoint> scheduledStopPoints = new ArrayList<>(scheduledStopovers.size());
            int[] idx = {1};
            scheduledStopovers.forEach(stopover -> {
                ScheduledStopPoint scheduledStopPoint = objectFactory().createScheduledStopPoint()
                        .withVersion("any")
                        .withId(String.format("%s:ScheduledStopPoint:%s10100%d", getAvinorConfig().getId(), scheduledFlight.getAirlineFlightId(), idx[0]))
                        .withName(createMultilingualString(stopover.getAirportName()));
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
                    .withVersion("any")
                    .withRef(String.format("%s:ScheduledStopPoint:%s10100%d", getAvinorConfig().getId(), flightId, idx[0])); // @todo: fix id generator
            StopPointInJourneyPattern stopPointInJourneyPattern = objectFactory().createStopPointInJourneyPattern()
                    .withVersion("any")
                    .withId(String.format("%s:StopPointInJourneyPattern:%s10100%d", getAvinorConfig().getId(), flightId, idx[0])) // @todo: fix some id generator-counter here...
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
                .withVersion("any")
                .withRef(route.getId());
        return objectFactory().createJourneyPattern()
                .withVersion("any")
                .withId(String.format("%s:JourneyPattern:%s101", getAvinorConfig().getId(), flightId)) // @todo: fix id generator
                .withRouteRef(routeRefStructure)
                .withPointsInSequence(pointsInJourneyPattern);
    }

    /**
     * @todo: fix problem with id reference for the StopPlaceRefStructure
     */
    public List<PassengerStopAssignment> createStopAssignments(List<ScheduledStopPoint> scheduledStopPoints, List<StopPlace> stopPlaces, String flightId) {
        List<PassengerStopAssignment> stopAssignments = new ArrayList<>(scheduledStopPoints.size());
        int index = 1;
        Iterator<ScheduledStopPoint> stopPointsIterator = scheduledStopPoints.iterator();
        Iterator<StopPlace> stopPlacesIterator = stopPlaces.iterator();
        while (stopPointsIterator.hasNext() && stopPlacesIterator.hasNext() && index <= scheduledStopPoints.size() && index <= stopPlaces.size()) {
            ScheduledStopPointRefStructure scheduledStopPointRef = objectFactory().createScheduledStopPointRefStructure()
                    .withVersion("any")
                    .withRef(stopPointsIterator.next().getId());
            StopPlaceRefStructure stopPlaceRef = objectFactory().createStopPlaceRefStructure()
                    .withVersion("any")
                    .withRef(stopPlacesIterator.next().getId());
            PassengerStopAssignment passengerStopAssignment = objectFactory().createPassengerStopAssignment()
                    .withVersion("any")
                    .withOrder(new BigInteger(Integer.toString(index)))
                    .withId(String.format("%s:PassengerStopAssignment:%s10100%d", getAvinorConfig().getId(), flightId, index)) // @todo: fix the id generation
                    .withScheduledStopPointRef(scheduledStopPointRef);
                    // .withStopPlaceRef(stopPlaceRef);
            stopAssignments.add(passengerStopAssignment);
            index++;
        }
        return stopAssignments;
    }

    public MultilingualString createMultilingualString(String value) {
        return objectFactory().createMultilingualString().withValue(value);
    }

    public Operator resolveOperatorFromIATA(String airlineIATA) {
        if (airlineIATA.equalsIgnoreCase(AirlineIATA.SK.name())) {
            return createSASOperator();
        } else if (airlineIATA.equalsIgnoreCase(AirlineIATA.DY.name())) {
            return createNorwegianOperator();
        } else if (airlineIATA.equalsIgnoreCase(AirlineIATA.WF.name())) {
            return createWideroeOperator();
        } else {
            return createUnknowOperator(airlineIATA);
        }
    }

    @Bean
    public ObjectFactory objectFactory() {
        return new ObjectFactory();
    }

    public Codespace avinorCodespace() {
        return objectFactory().createCodespace()
                .withId(getAvinorConfig().getName().toLowerCase())
                .withXmlns(getAvinorConfig().getId())
                .withXmlnsUrl(getAvinorConfig().getUrl());
    }

    public Codespace nhrCodespace() {
        return objectFactory().createCodespace()
                .withId(getNhrConfig().getId().toLowerCase())
                .withXmlns(getNhrConfig().getId())
                .withXmlnsUrl(getNhrConfig().getUrl());
    }

    public Authority createAuthority() {
        List<JAXBElement<?>> organisationRest = createOrganisationRest(
                getAvinorConfig().getCompanyNumber(), getAvinorConfig().getName(),
                getAvinorConfig().getLegalName(), getAvinorConfig().getPhone(),
                getAvinorConfig().getUrl(), OrganisationTypeEnumeration.AUTHORITY);
        return objectFactory().createAuthority()
                .withVersion("any")
                .withId(String.format("%s:Company:1", getAvinorConfig().getId()))
                .withRest(organisationRest);
    }

    public Operator createSASOperator() {
        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                getSasConfig().getCompanyNumber(), getSasConfig().getName(),
                getSasConfig().getLegalName(), getSasConfig().getPhone(),
                getSasConfig().getUrl(), OrganisationTypeEnumeration.OPERATOR);
        return objectFactory().createOperator()
                .withVersion("any")
                .withId(String.format("%s:Company:2", getSasConfig().getName()))
                .withRest(operatorRest);
    }

    public Operator createWideroeOperator() {
        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                getWideroeConfig().getCompanyNumber(), getWideroeConfig().getName(),
                getWideroeConfig().getLegalName(), getWideroeConfig().getPhone(),
                getWideroeConfig().getUrl(), OrganisationTypeEnumeration.OPERATOR);
        return objectFactory().createOperator()
                .withVersion("any")
                .withId(String.format("%s:Company:2", getWideroeConfig().getName()))
                .withRest(operatorRest);
    }

    public Operator createNorwegianOperator() {
        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                getNorwegianConfig().getCompanyNumber(), getNorwegianConfig().getName(),
                getNorwegianConfig().getLegalName(), getNorwegianConfig().getPhone(),
                getNorwegianConfig().getUrl(), OrganisationTypeEnumeration.OPERATOR);
        return objectFactory().createOperator()
                .withVersion("any")
                .withId(String.format("%s:Company:2", getNorwegianConfig().getName()))
                .withRest(operatorRest);
    }

    private Operator createUnknowOperator(String airlineIATA) {
        List<JAXBElement<?>> dummyOperatorRest = createOrganisationRest(
                "999999999",
                airlineIATA,
                airlineIATA,
                "0047 999 99 999",
                String.format("http://%s.no/", airlineIATA),
                OrganisationTypeEnumeration.OPERATOR
        );
        return objectFactory().createOperator()
                .withVersion("any")
                .withId(String.format("UNKNOWN-%s:Company:2", airlineIATA))
                .withRest(dummyOperatorRest);
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

    public NhrAuthorityConfig getNhrConfig() {
        return nhrConfig;
    }

    @Autowired
    public void setNhrConfig(NhrAuthorityConfig nhrConfig) {
        this.nhrConfig = nhrConfig;
    }

    public AvinorAuthorityConfig getAvinorConfig() {
        return avinorConfig;
    }

    @Autowired
    public void setAvinorConfig(AvinorAuthorityConfig avinorConfig) {
        this.avinorConfig = avinorConfig;
    }

    public SasOperatorConfig getSasConfig() {
        return sasConfig;
    }

    @Autowired
    public void setSasConfig(SasOperatorConfig sasConfig) {
        this.sasConfig = sasConfig;
    }

    public WideroeOperatorConfig getWideroeConfig() {
        return wideroeConfig;
    }

    @Autowired
    public void setWideroeConfig(WideroeOperatorConfig wideroeConfig) {
        this.wideroeConfig = wideroeConfig;
    }

    public NorwegianOperatorConfig getNorwegianConfig() {
        return norwegianConfig;
    }

    @Autowired
    public void setNorwegianConfig(NorwegianOperatorConfig norwegianConfig) {
        this.norwegianConfig = norwegianConfig;
    }

}
