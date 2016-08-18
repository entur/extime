package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.config.*;
import no.rutebanken.extime.model.*;
import no.rutebanken.netex.model.*;
import no.rutebanken.netex.model.PublicationDeliveryStructure.DataObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component(value = "scheduledFlightToNetexConverter")
public class ScheduledFlightToNetexConverter {

    private AvinorAuthorityConfig avinorConfig;
    private NhrAuthorityConfig nhrConfig;
    private SasOperatorConfig sasConfig;
    private WideroeOperatorConfig wideroeConfig;
    private NorwegianOperatorConfig norwegianConfig;

    public JAXBElement<PublicationDeliveryStructure> convertToNetex(ScheduledFlight scheduledFlight) {
        LocalDate dateOfOperation = scheduledFlight.getDateOfOperation();
        String routePath = String.format("%s-%s", scheduledFlight.getDepartureAirportName(), scheduledFlight.getArrivalAirportName());
        String flightId = scheduledFlight.getAirlineFlightId();

        List<StopPlace> stopPlaces = createStopPlaces(scheduledFlight);
        List<ScheduledStopPoint> scheduledStopPoints = createScheduledStopPoints(scheduledFlight);
        List<RoutePoint> routePoints = createRoutePoints(scheduledStopPoints, flightId);
        List<PassengerStopAssignment> stopAssignments = createStopAssignments(scheduledStopPoints, stopPlaces, flightId);

        Direction direction = createDirection(flightId);
        Route route = createRoute(routePoints, flightId, routePath, direction);
        Operator operator = resolveOperatorFromIATA(scheduledFlight.getAirlineIATA());
        Line line = createLine(route, flightId, routePath, operator);
        ServicePattern servicePattern = createServicePattern(flightId, routePath, route, scheduledStopPoints);
        List<DayType> dayTypes = Collections.singletonList(createDayType(flightId));
        List<ServiceJourney> serviceJourneys = createServiceJourneyList(scheduledFlight, dayTypes, servicePattern, line, scheduledStopPoints);

        Frames_RelStructure frames = objectFactory().createFrames_RelStructure();
        frames.getCommonFrame().add(createResourceFrame(scheduledFlight.getAirlineIATA(), operator));
        frames.getCommonFrame().add(createSiteFrame(stopPlaces));
        frames.getCommonFrame().add(createServiceFrame(direction, flightId, routePoints, route,
                line, scheduledStopPoints, servicePattern, stopAssignments));
        frames.getCommonFrame().add(createTimetableFrame(dateOfOperation, serviceJourneys));
        frames.getCommonFrame().add(createServiceCalendarFrame(dayTypes));

        JAXBElement<CompositeFrame> compositeFrame = createCompositeFrame(flightId, frames);
        PublicationDeliveryStructure publicationDeliveryStructure = createPublicationDeliveryStructure(compositeFrame, flightId, routePath);
        return objectFactory().createPublicationDelivery(publicationDeliveryStructure);
    }

    // @todo: how to remove the zone id, when the timestamp is represented by a ZonedDateTime object, change jaxb model?
    // @todo: maybe it is better to show dates and times in the current (Europe/Oslo) timezone?
    public PublicationDeliveryStructure createPublicationDeliveryStructure(
            JAXBElement<CompositeFrame> compositeFrame, String flightId, String routePath) {
        DataObjects dataObjects = objectFactory().createPublicationDeliveryStructureDataObjects();
        dataObjects.getCompositeFrameOrCommonFrame().add(compositeFrame);
        return objectFactory().createPublicationDeliveryStructure()
                .withVersion("1.0")
                //.withPublicationTimestamp(ZonedDateTime.now())
                //.withPublicationTimestamp(DateTimeFormatter.ISO_INSTANT.parse("2016-08-16T08:24:21Z", Instant::from))
                .withPublicationTimestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2016-08-16T08:24:21Z", OffsetDateTime::from))
                .withParticipantRef(getAvinorConfig().getId()) // should this be Avinor or the actual flight airline?
                .withDescription(createMultilingualString(String.format("Flight %s : %s", flightId, routePath)))
                .withDataObjects(dataObjects);
    }

    public JAXBElement<CompositeFrame> createCompositeFrame(String flightId, Frames_RelStructure frames) {
        Codespaces_RelStructure codespaces = objectFactory().createCodespaces_RelStructure()
                .withCodespaceRefOrCodespace(Arrays.asList(avinorCodespace(), nhrCodespace()));
        CompositeFrame compositeFrame = objectFactory().createCompositeFrame()
                .withVersion("1")
                //.withCreated(ZonedDateTime.now())
                //.withCreated(DateTimeFormatter.ISO_INSTANT.parse("2016-08-16T08:24:21Z", Instant::from))
                .withCreated(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2016-08-16T08:24:21Z", OffsetDateTime::from))
                .withId(String.format("%s:Norway:CompositeFrame:%s", getAvinorConfig().getId(), flightId))
                .withCodespaces(codespaces)
                .withFrames(frames);
        return objectFactory().createCompositeFrame(compositeFrame);
    }

    /**
     * @todo: Consider adding a parameter for default codespace (avinor), and not use the avinor name from config
     */
    public JAXBElement<ResourceFrame> createResourceFrame(String airlineIATA, Operator operator) {
        CodespaceRefStructure codespaceRefStructure = objectFactory().createCodespaceRefStructure()
                .withRef(avinorCodespace().getId());

        LocaleStructure localeStructure = objectFactory().createLocaleStructure()
                .withTimeZone("CET")
                .withSummerTimeZone("CEST")
                .withDefaultLanguage("no");

        VersionFrameDefaultsStructure versionFrameDefaultsStructure = objectFactory().createVersionFrameDefaultsStructure()
                .withDefaultCodespaceRef(codespaceRefStructure)
                .withDefaultLocale(localeStructure);

        OrganisationsInFrame_RelStructure organisationsInFrame = objectFactory().createOrganisationsInFrame_RelStructure();
        organisationsInFrame.getOrganisation_().add(objectFactory().createAuthority(createAuthority()));
        organisationsInFrame.getOrganisation_().add(objectFactory().createOperator(operator));

        ResourceFrame resourceFrame = objectFactory().createResourceFrame()
                .withVersion("any")
                .withId(String.format("%s:ResourceFrame:RF1", getAvinorConfig().getId()))
                .withFrameDefaults(versionFrameDefaultsStructure)
                .withOrganisations(organisationsInFrame);
        return objectFactory().createResourceFrame(resourceFrame);
    }

    public JAXBElement<SiteFrame> createSiteFrame(List<StopPlace> stopPlaces) {
        StopPlacesInFrame_RelStructure stopPlacesInFrameRelStructure = objectFactory().createStopPlacesInFrame_RelStructure()
                        .withStopPlace(stopPlaces);
        SiteFrame siteFrame = objectFactory().createSiteFrame()
                .withVersion("any")
                .withId(String.format("%s:SiteFrame:SF01", getAvinorConfig().getId()))
                .withStopPlaces(stopPlacesInFrameRelStructure);
        return objectFactory().createSiteFrame(siteFrame);
    }

    public JAXBElement<ServiceFrame> createServiceFrame(Direction direction, String flightId, List<RoutePoint> routePoints,
                                                        Route route, Line line, List<ScheduledStopPoint> scheduledStopPoints,
                                                        ServicePattern servicePattern, List<PassengerStopAssignment> stopAssignments) {
        // @todo: fix the zoned date time?
        Network network = objectFactory().createNetwork()
                .withVersion("1")
                //.withChanged(ZonedDateTime.now())
                //.withChanged(DateTimeFormatter.ISO_INSTANT.parse("2016-08-16T08:24:21Z", Instant::from))
                .withChanged(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2016-08-16T08:24:21Z", OffsetDateTime::from))
                .withId(String.format("%s:GroupOfLine:%s", getAvinorConfig().getId(), getAvinorConfig().getName()))
                .withName(createMultilingualString(getAvinorConfig().getName()));

        DirectionsInFrame_RelStructure directionsInFrame = objectFactory().createDirectionsInFrame_RelStructure()
                .withDirection(direction);

        RoutePointsInFrame_RelStructure routePointsInFrame = objectFactory().createRoutePointsInFrame_RelStructure()
                .withRoutePoint(routePoints);

        RoutesInFrame_RelStructure routesInFrame = objectFactory().createRoutesInFrame_RelStructure();
        routesInFrame.getRoute_().add(objectFactory().createRoute(route));

        LinesInFrame_RelStructure linesInFrame = objectFactory().createLinesInFrame_RelStructure();
        linesInFrame.getLine_().add(objectFactory().createLine(line));

        ScheduledStopPointsInFrame_RelStructure scheduledStopPointsInFrame = objectFactory().createScheduledStopPointsInFrame_RelStructure()
                .withScheduledStopPoint(scheduledStopPoints);

        ServicePatternsInFrame_RelStructure servicePatternsInFrame = objectFactory().createServicePatternsInFrame_RelStructure()
                .withServicePatternOrJourneyPatternView(servicePattern);

        StopAssignmentsInFrame_RelStructure stopAssignmentsInFrame = objectFactory().createStopAssignmentsInFrame_RelStructure();
        stopAssignments.forEach(stopAssignment ->
                stopAssignmentsInFrame.getStopAssignment().add(objectFactory().createPassengerStopAssignment(stopAssignment))
        );

        ServiceFrame serviceFrame = objectFactory().createServiceFrame()
                .withVersion("any")
                .withId(String.format("%s:ServiceFrame:%s", getAvinorConfig().getId(), flightId))
                .withNetwork(network)
                .withDirections(directionsInFrame)
                .withRoutePoints(routePointsInFrame)
                .withRoutes(routesInFrame)
                .withLines(linesInFrame)
                .withScheduledStopPoints(scheduledStopPointsInFrame)
                .withServicePatterns(servicePatternsInFrame)
                .withStopAssignments(stopAssignmentsInFrame);
        return objectFactory().createServiceFrame(serviceFrame);
    }

    public JAXBElement<ServiceCalendarFrame> createServiceCalendarFrame(List<DayType> dayTypes) {
        DayTypesInFrame_RelStructure dayTypesStructure = objectFactory().createDayTypesInFrame_RelStructure();
        dayTypes.forEach(dayType -> dayTypesStructure.getDayType_().add(objectFactory().createDayType(dayType)));
        ServiceCalendarFrame serviceCalendarFrame = objectFactory().createServiceCalendarFrame()
                .withVersion("1")
                .withId("avinor:scf:01")
                .withDayTypes(dayTypesStructure);
        return objectFactory().createServiceCalendarFrame(serviceCalendarFrame);
    }

    public DayType createDayType(String flightId) {
        List<DayOfWeekEnumeration> daysOfWeek = Arrays.asList(
                DayOfWeekEnumeration.MONDAY,
                DayOfWeekEnumeration.TUESDAY,
                DayOfWeekEnumeration.WEDNESDAY,
                DayOfWeekEnumeration.THURSDAY,
                DayOfWeekEnumeration.FRIDAY
        );

        PropertyOfDay propertyOfDayWeekDays = objectFactory().createPropertyOfDay();
        propertyOfDayWeekDays.getDaysOfWeek().addAll(daysOfWeek);
        PropertiesOfDay_RelStructure propertiesOfDay = objectFactory().createPropertiesOfDay_RelStructure()
                .withPropertyOfDay(propertyOfDayWeekDays);

        return objectFactory().createDayType()
                .withVersion("any")
                .withId(String.format("%s:dt:weekday", flightId))
                .withName(createMultilingualString("Ukedager (mandag til fredag)"))
                .withProperties(propertiesOfDay);
    }

    public JAXBElement<TimetableFrame> createTimetableFrame(LocalDate dateOfOperation, List<ServiceJourney> serviceJourneys) {
        ValidityConditions_RelStructure validityConditionsRelStructure = objectFactory().createValidityConditions_RelStructure()
                .withValidityConditionRefOrValidBetweenOrValidityCondition_(createAvailabilityCondition(dateOfOperation, Boolean.TRUE));
        JourneysInFrame_RelStructure journeysInFrameRelStructure = objectFactory().createJourneysInFrame_RelStructure();
        journeysInFrameRelStructure.getDatedServiceJourneyOrDeadRunOrServiceJourney().addAll(serviceJourneys);

        TimetableFrame timetableFrame = objectFactory().createTimetableFrame()
                .withVersion("any")
                .withId(String.format("%s:TimetableFrame:TF01", getAvinorConfig().getId()))
                .withValidityConditions(validityConditionsRelStructure)
                .withName(createMultilingualString("Rute for 2016"))
                .withVehicleModes(VehicleModeEnumeration.AIR)
                .withVehicleJourneys(journeysInFrameRelStructure);
        return objectFactory().createTimetableFrame(timetableFrame);
    }

    public List<StopPlace> createStopPlaces(ScheduledFlight scheduledFlight) throws IllegalArgumentException {
        if (scheduledFlight instanceof ScheduledDirectFlight) {
            StopPlace departureStopPlace = objectFactory().createStopPlace()
                    .withVersion("1")
                    .withId("NHR:StopArea:03011537") // @todo: retrieve the actual stopplace id from NHR
                    .withName(createMultilingualString(scheduledFlight.getDepartureAirportName()))
                    .withShortName(createMultilingualString(scheduledFlight.getDepartureAirportIATA()))
                    .withTransportMode(VehicleModeEnumeration.AIR)
                    .withStopPlaceType(StopTypeEnumeration.AIRPORT);
            StopPlace arrivalStopPlace = objectFactory().createStopPlace()
                    .withVersion("1")
                    .withId("NHR:StopArea:03011521")  // @todo: retrieve the actual stopplace id from NHR
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
                        .withId(String.format("NHR:StopArea:0301152%d", idx[0]))  // @todo: retrieve the actual stopplace id from NHR
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

    // @todo: look over this, and change from- and to date to localdate instead of instant
    public JAXBElement<AvailabilityCondition> createAvailabilityCondition(LocalDate dateOfOperation, Boolean isAvailable) {
        AvailabilityCondition availabilityCondition = objectFactory().createAvailabilityCondition()
                .withVersion("any")
                .withId(String.format("%s:AvailabilityCondition:1", getAvinorConfig().getId()))
                //.withFromDate(ZonedDateTime.of(dateOfOperation, LocalTime.MIN, ZoneId.of("Z")))
                //.withFromDate(DateTimeFormatter.ISO_INSTANT.parse("2016-08-16T08:24:21Z", Instant::from))
                .withFromDate(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2016-08-16T08:24:21Z", OffsetDateTime::from))
                //.withToDate(ZonedDateTime.of(dateOfOperation, LocalTime.MIN, ZoneId.of("Z")))
                //.withToDate(DateTimeFormatter.ISO_INSTANT.parse("2016-08-16T08:24:21Z", Instant::from))
                .withToDate(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2016-08-16T08:24:21Z", OffsetDateTime::from))
                .withIsAvailable(isAvailable);
        return objectFactory().createAvailabilityCondition(availabilityCondition);
    }

    // @todo: add the journey pattern reference to ServiceJourney?
    public List<ServiceJourney> createServiceJourneyList(ScheduledFlight scheduledFlight, List<DayType> dayTypes,
                                                         ServicePattern servicePattern, Line line, List<ScheduledStopPoint> scheduledStopPoints) throws IllegalArgumentException {
        List<ServiceJourney> serviceJourneyList = new ArrayList<>();
        if (scheduledFlight instanceof ScheduledDirectFlight) {
            TimetabledPassingTimes_RelStructure passingTimesRelStructure = objectFactory().createTimetabledPassingTimes_RelStructure();
            List<StopPointInJourneyPattern> stopPointInJourneyPatternList = servicePattern.getPointsInSequence().getStopPointInJourneyPattern();

            DayTypeRefs_RelStructure dayTypeStructure = objectFactory().createDayTypeRefs_RelStructure();
            dayTypes.forEach(dayType -> {
                DayTypeRefStructure dayTypeRef = objectFactory().createDayTypeRefStructure().withRef(dayType.getId());
                dayTypeStructure.getDayTypeRef().add(objectFactory().createDayTypeRef(dayTypeRef));
            });

            StopPointInJourneyPatternRefStructure departureStopPointInJourneyPattern = objectFactory().createStopPointInJourneyPatternRefStructure()
                    .withVersion("any")
                    .withRef(stopPointInJourneyPatternList.get(0).getId());
            TimetabledPassingTime departurePassingTime = objectFactory().createTimetabledPassingTime()
                    .withPointInJourneyPatternRef(objectFactory().createStopPointInJourneyPatternRef(departureStopPointInJourneyPattern))
                    .withDepartureTime(scheduledFlight.getTimeOfDeparture());
            passingTimesRelStructure.withTimetabledPassingTime(departurePassingTime);

            StopPointInJourneyPatternRefStructure arrivalStopPointInJourneyPattern = objectFactory().createStopPointInJourneyPatternRefStructure()
                    .withVersion("any")
                    .withRef(stopPointInJourneyPatternList.get(1).getId());
            TimetabledPassingTime arrivalPassingTime = objectFactory().createTimetabledPassingTime()
                    .withPointInJourneyPatternRef(objectFactory().createStopPointInJourneyPatternRef(arrivalStopPointInJourneyPattern))
                    .withDepartureTime(scheduledFlight.getTimeOfArrival());
            passingTimesRelStructure.withTimetabledPassingTime(arrivalPassingTime);

            ServiceJourney datedServiceJourney = objectFactory().createServiceJourney()
                    .withVersion("any")
                    .withId(String.format("%s:ServiceJourney:%s", getAvinorConfig().getId(), scheduledFlight.getAirlineFlightId()))
                    .withDepartureTime(scheduledFlight.getTimeOfDeparture())
                    .withDayTypes(dayTypeStructure)
                    //.withJourneyPatternRef() @todo: implement!
                    .withLineRef(objectFactory().createLineRef(objectFactory().createLineRefStructure().withRef(line.getId())))
                    .withPassingTimes(passingTimesRelStructure);
            serviceJourneyList.add(datedServiceJourney);
            return serviceJourneyList;
        } else if (scheduledFlight instanceof ScheduledStopoverFlight) {
            TimetabledPassingTimes_RelStructure passingTimesRelStructure = objectFactory().createTimetabledPassingTimes_RelStructure();
            List<StopPointInJourneyPattern> journeyPatternStopPoints = servicePattern.getPointsInSequence().getStopPointInJourneyPattern();

            List<ScheduledStopover> scheduledStopovers = ((ScheduledStopoverFlight) scheduledFlight).getScheduledStopovers();
            Iterator<ScheduledStopover> stopoversiterator = scheduledStopovers.iterator();
            Iterator<StopPointInJourneyPattern> journeyPatternStopPointsIterator = journeyPatternStopPoints.iterator();

            while (stopoversiterator.hasNext() && journeyPatternStopPointsIterator.hasNext()) {
                ScheduledStopover scheduledStopover = stopoversiterator.next();
                StopPointInJourneyPattern stopPointInJourneyPattern = journeyPatternStopPointsIterator.next();
                StopPointInJourneyPatternRefStructure stopPointInJourneyPatternRef = objectFactory().createStopPointInJourneyPatternRefStructure()
                        .withVersion("any")
                        .withRef(stopPointInJourneyPattern.getId());
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
            ServiceJourney datedServiceJourney = objectFactory().createServiceJourney()
                    .withVersion("any")
                    .withId(String.format("%s:ServiceJourney:%s", getAvinorConfig().getId(), scheduledFlight.getAirlineFlightId()))
                    .withDepartureTime(scheduledStopovers.get(0).getDepartureTime())
                    //.withDayTypes() @todo: implement!
                    //.withJourneyPatternRef() @todo: implement!
                    .withLineRef(objectFactory().createLineRef(objectFactory().createLineRefStructure().withRef(line.getId())))
                    .withPassingTimes(passingTimesRelStructure);
            serviceJourneyList.add(datedServiceJourney);
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

    public Route createRoute(List<RoutePoint> routePoints, String flightId, String routePath, Direction direction) {
        PointsOnRoute_RelStructure pointsOnRoute = objectFactory().createPointsOnRoute_RelStructure();
        int[] idx = {1};
        routePoints.forEach(routePoint -> {
            RoutePointRefStructure routePointReference = objectFactory().createRoutePointRefStructure()
                    //.withVersion("1") // @todo: temp. disable to prevent id check, enable and fix
                    .withRef(routePoint.getId());
            PointOnRoute pointOnRoute = objectFactory().createPointOnRoute()
                    .withVersion("any")
                    .withId(String.format("%s:PointOnRoute:%s101001-%d", getAvinorConfig().getId(), flightId, idx[0])) // @todo: fix generation of serial numbers
                    .withPointRef(objectFactory().createRoutePointRef(routePointReference));
            pointsOnRoute.getPointOnRoute().add(pointOnRoute);
            idx[0]++;
        });

        DirectionRefStructure directionRefStructure = objectFactory().createDirectionRefStructure()
                .withRef(direction.getId());
        return objectFactory().createRoute()
                .withVersion("1")
                .withId(String.format("%s:Route:%s101", getAvinorConfig().getId(), flightId))
                .withName(createMultilingualString(String.format("%s: %s", flightId, routePath)))
                .withPointsInSequence(pointsOnRoute)
                .withDirectionRef(directionRefStructure);
    }

    private Line createLine(Route route, String flightId, String routePath, Operator operator) {
        RouteRefStructure routeRefStructure = objectFactory().createRouteRefStructure()
                .withVersion("1")
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
                    .withVersion("1")
                    .withId(String.format("%s:StopPoint:%s101001", getAvinorConfig().getId(), scheduledFlight.getAirlineFlightId()))
                    .withName(createMultilingualString(scheduledFlight.getDepartureAirportName()));
            ScheduledStopPoint scheduledArrivalStopPoint = objectFactory().createScheduledStopPoint()
                    .withVersion("1")
                    .withId(String.format("%s:StopPoint:%s101002", getAvinorConfig().getId(), scheduledFlight.getAirlineFlightId()))
                    .withName(createMultilingualString(scheduledFlight.getArrivalAirportName()));
            return Lists.newArrayList(scheduledDepartureStopPoint, scheduledArrivalStopPoint);
        } else if (scheduledFlight instanceof ScheduledStopoverFlight) {
            List<ScheduledStopover> scheduledStopovers = ((ScheduledStopoverFlight) scheduledFlight).getScheduledStopovers();
            List<ScheduledStopPoint> scheduledStopPoints = new ArrayList<>(scheduledStopovers.size());
            int[] idx = {1};
            scheduledStopovers.forEach(stopover -> {
                ScheduledStopPoint scheduledStopPoint = objectFactory().createScheduledStopPoint()
                        .withVersion("1")
                        .withId(String.format("%s:StopPoint:%s10100%d", getAvinorConfig().getId(), scheduledFlight.getAirlineFlightId(), idx[0]))
                        .withName(createMultilingualString(stopover.getAirportName()));
                scheduledStopPoints.add(scheduledStopPoint);
                idx[0]++;
            });
            return scheduledStopPoints;
        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }
    }

    public ServicePattern createServicePattern(String flightId, String routePath, Route route, List<ScheduledStopPoint> scheduledStopPoints) {
        StopPointsInJourneyPattern_RelStructure stopPointsInJourneyPattern = objectFactory().createStopPointsInJourneyPattern_RelStructure();
        int[] idx = {1};
        scheduledStopPoints.forEach(stopPoint -> {
            ScheduledStopPointRefStructure scheduledStopPointRefStructure = objectFactory().createScheduledStopPointRefStructure()
                    .withVersion("1")
                    .withRef(String.format("%s:StopPoint:%s101001", getAvinorConfig().getId(), flightId)); // @todo: fix id generator
            StopPointInJourneyPattern stopPointInJourneyPattern = objectFactory().createStopPointInJourneyPattern()
                    .withVersion("1")
                    .withId(String.format("%s:StopPointInJourneyPattern:%s10100%d", getAvinorConfig().getId(), flightId, idx[0])) // @todo: fix some id generator-counter here...
                    .withOrder(new BigInteger(Integer.toString(idx[0])))
                    .withScheduledStopPointRef(objectFactory().createScheduledStopPointRef(scheduledStopPointRefStructure));
            stopPointsInJourneyPattern.getStopPointInJourneyPattern().add(stopPointInJourneyPattern);
            idx[0]++;
        });
        RouteRefStructure routeRefStructure = objectFactory().createRouteRefStructure()
                .withVersion("1")
                .withRef(route.getId());
        return objectFactory().createServicePattern()
                .withVersion("1")
                .withId(String.format("%s:ServicePattern:%s101", getAvinorConfig().getId(), flightId)) // @todo: fix id generator
                .withName(createMultilingualString(routePath))
                .withRouteRef(routeRefStructure)
                .withPointsInSequence(stopPointsInJourneyPattern);
    }

    public List<PassengerStopAssignment> createStopAssignments(List<ScheduledStopPoint> scheduledStopPoints, List<StopPlace> stopPlaces, String flightId) {
        List<PassengerStopAssignment> stopAssignments = new ArrayList<>(scheduledStopPoints.size());
        int index = 1;
        Iterator<ScheduledStopPoint> stopPointsIterator = scheduledStopPoints.iterator();
        Iterator<StopPlace> stopPlacesIterator = stopPlaces.iterator();
        while (stopPointsIterator.hasNext() && stopPlacesIterator.hasNext() && index <= scheduledStopPoints.size() && index <= stopPlaces.size()) {
            ScheduledStopPointRefStructure scheduledStopPointRef = objectFactory().createScheduledStopPointRefStructure()
                    .withVersion("1")
                    .withRef(stopPointsIterator.next().getId());
            StopPlaceRefStructure stopPlaceRef = objectFactory().createStopPlaceRefStructure()
                    .withVersion("1")
                    .withRef(stopPlacesIterator.next().getId());
            PassengerStopAssignment passengerStopAssignment = objectFactory().createPassengerStopAssignment()
                    .withVersion("any")
                    .withOrder(new BigInteger(Integer.toString(index)))
                    .withId(String.format("%s:PassengerStopAssignment:%s10100%d", getAvinorConfig().getId(), flightId, index)) // @todo: fix the id generation
                    .withScheduledStopPointRef(scheduledStopPointRef)
                    .withStopPlaceRef(stopPlaceRef);
            stopAssignments.add(passengerStopAssignment);
            index++;
        }
        return stopAssignments;
    }

    public Direction createDirection(String flightId) {
        return objectFactory().createDirection()
                .withVersion("any")
                .withId(String.format("%s:Route:%s101:Direction", getAvinorConfig().getId(), flightId)) // @todo: change id to 3-part pattern AVI:Direction:id
                .withName(createMultilingualString("Outbound"))
                .withDirectionType(DirectionTypeEnumeration.OUTBOUND);
    }

    public MultilingualString createMultilingualString(String value) {
        return objectFactory().createMultilingualString()
                //.withLang("no")
                .withValue(value);
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
                .withVersion("1")
                .withId(String.format("%s:Company:1", getAvinorConfig().getId()))
                .withRest(organisationRest);
    }

    public Operator createSASOperator() {
        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                getSasConfig().getCompanyNumber(), getSasConfig().getName(),
                getSasConfig().getLegalName(), getSasConfig().getPhone(),
                getSasConfig().getUrl(), OrganisationTypeEnumeration.OPERATOR);
        return objectFactory().createOperator()
                .withVersion("1")
                .withId(String.format("%s:Company:2", getSasConfig().getName()))
                .withRest(operatorRest);
    }

    public Operator createWideroeOperator() {
        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                getWideroeConfig().getCompanyNumber(), getWideroeConfig().getName(),
                getWideroeConfig().getLegalName(), getWideroeConfig().getPhone(),
                getWideroeConfig().getUrl(), OrganisationTypeEnumeration.OPERATOR);
        return objectFactory().createOperator()
                .withVersion("1")
                .withId(String.format("%s:Company:2", getWideroeConfig().getName()))
                .withRest(operatorRest);
    }

    public Operator createNorwegianOperator() {
        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                getNorwegianConfig().getCompanyNumber(), getNorwegianConfig().getName(),
                getNorwegianConfig().getLegalName(), getNorwegianConfig().getPhone(),
                getNorwegianConfig().getUrl(), OrganisationTypeEnumeration.OPERATOR);
        return objectFactory().createOperator()
                .withVersion("1")
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
                .withVersion("1")
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
                //.withFurtherDetails(createMultilingualString(furtherDetails));
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
