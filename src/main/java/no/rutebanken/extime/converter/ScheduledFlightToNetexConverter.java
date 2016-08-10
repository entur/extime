package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.config.*;
import no.rutebanken.extime.model.AirlineIATA;
import no.rutebanken.extime.model.ScheduledDirectFlight;
import no.rutebanken.extime.model.ScheduledStopover;
import no.rutebanken.extime.model.ScheduledStopoverFlight;
import no.rutebanken.netex.model.*;
import no.rutebanken.netex.model.PublicationDeliveryStructure.DataObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.namespace.QName;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Component(value = "scheduledFlightToNetexConverter")
public class ScheduledFlightToNetexConverter {

    private AvinorAuthorityConfig avinorConfig;
    private NhrAuthorityConfig nhrConfig;
    private SasOperatorConfig sasConfig;
    private WideroeOperatorConfig wideroeConfig;
    private NorwegianOperatorConfig norwegianConfig;

    public JAXBElement<PublicationDeliveryStructure> convertToNetex(ScheduledDirectFlight directFlight) {
        LocalDate dateOfOperation = directFlight.getDateOfOperation();
        String routePath = String.format("%s-%s", directFlight.getDepartureAirportName(), directFlight.getArrivalAirportName());
        String flightId = directFlight.getAirlineFlightId();

        List<StopPlace> stopPlaces = createStopPlaces(directFlight);
        List<ScheduledStopPoint> scheduledStopPoints = createScheduledStopPoints(directFlight);
        List<RoutePoint> routePoints = createRoutePoints(scheduledStopPoints, flightId);
        List<PassengerStopAssignment> stopAssignments = createStopAssignments(scheduledStopPoints, stopPlaces, flightId);

        Direction direction = createDirection(flightId);
        Route route = createRoute(routePoints, flightId, routePath, direction);
        Line line = createLine(route, flightId, routePath);
        ServicePattern servicePattern = createServicePattern(flightId, routePath, route, scheduledStopPoints);
        List<DayType> dayTypes = Arrays.asList(createDayType(flightId));
        List<ServiceJourney> serviceJourneys = createServiceJourneyList(directFlight, dayTypes, servicePattern, line, scheduledStopPoints);

        Frames_RelStructure frames = objectFactory().createFrames_RelStructure();
        frames.getCommonFrame().add(createResourceFrame(directFlight.getAirlineIATA()));
        frames.getCommonFrame().add(createSiteFrame(stopPlaces));
        frames.getCommonFrame().add(createServiceFrame(direction, flightId, routePoints, route,
                line, scheduledStopPoints, servicePattern, stopAssignments));
        frames.getCommonFrame().add(createTimetableFrame(dateOfOperation, serviceJourneys));
        frames.getCommonFrame().add(createServiceCalendarFrame(dayTypes));

        JAXBElement<CompositeFrame> compositeFrame = createCompositeFrame(flightId, frames);
        PublicationDeliveryStructure publicationDeliveryStructure = createPublicationDeliveryStructure(compositeFrame, flightId, routePath);
        return objectFactory().createPublicationDelivery(publicationDeliveryStructure);
    }

    public JAXBElement<PublicationDeliveryStructure> convertToNetex(ScheduledStopoverFlight stopoverFlight) {
        LocalDate dateOfOperation = stopoverFlight.getDateOfOperation();
        String routePath = stopoverFlight.getRoutePath();
        String flightId = stopoverFlight.getFlightId();

        List<StopPlace> stopPlaces = createStopPlaces(stopoverFlight.getScheduledStopovers());
        List<ScheduledStopPoint> scheduledStopPoints = createScheduledStopPoints(stopoverFlight.getScheduledStopovers(), flightId);
        List<RoutePoint> routePoints = createRoutePoints(scheduledStopPoints, flightId);
        List<PassengerStopAssignment> stopAssignments = createStopAssignments(scheduledStopPoints, stopPlaces, flightId);

        Direction direction = createDirection(flightId);
        Route route = createRoute(routePoints, flightId, routePath, direction);
        Line line = createLine(route, flightId, routePath);
        ServicePattern servicePattern = createServicePattern(flightId, routePath, route, scheduledStopPoints);
        List<ServiceJourney> serviceJourneys = createServiceJourneyList(stopoverFlight, servicePattern, line, scheduledStopPoints);

        Frames_RelStructure frames = objectFactory().createFrames_RelStructure();
        frames.getCommonFrame().add(createResourceFrame(stopoverFlight.getAirlineIATA()));
        frames.getCommonFrame().add(createSiteFrame(stopPlaces));
        frames.getCommonFrame().add(createServiceFrame(direction, flightId, routePoints, route,
                line, scheduledStopPoints, servicePattern, stopAssignments));
        //framesRelStructure.getCommonFrame().add(createServiceCalendarFrame());
        frames.getCommonFrame().add(createTimetableFrame(dateOfOperation, serviceJourneys));

        JAXBElement<CompositeFrame> compositeFrame = createCompositeFrame(flightId, frames);
        PublicationDeliveryStructure publicationDeliveryStructure = createPublicationDeliveryStructure(compositeFrame, flightId, routePath);
        return objectFactory().createPublicationDelivery(publicationDeliveryStructure);
    }

    public PublicationDeliveryStructure createPublicationDeliveryStructure(
            JAXBElement<CompositeFrame> compositeFrame, String flightId, String routePath) {
        DataObjects dataObjects = objectFactory().createPublicationDeliveryStructureDataObjects();
        dataObjects.getCompositeFrameOrCommonFrame().add(compositeFrame);

        return objectFactory().createPublicationDeliveryStructure()
                .withVersion("1.0")
                .withPublicationTimestamp(ZonedDateTime.now())
                .withParticipantRef(getAvinorConfig().getId()) // should this be Avinor or the actual flight airline?
                .withDescription(createMultilingualString(String.format("Flight %s : %s", flightId, routePath)))
                .withDataObjects(dataObjects);
    }

    public JAXBElement<CompositeFrame> createCompositeFrame(String flightId, Frames_RelStructure frames) {
        Codespaces_RelStructure codespaces = objectFactory().createCodespaces_RelStructure()
                .withCodespaceRefOrCodespace(Arrays.asList(avinorCodespace(), nhrCodespace()));

        CompositeFrame compositeFrame = objectFactory().createCompositeFrame()
                .withVersion("1")
                .withCreated(ZonedDateTime.now())
                .withId(String.format("%s:Norway:CompositeFrame:%s", getAvinorConfig().getId(), flightId))
                .withCodespaces(codespaces)
                .withFrames(frames);
        return objectFactory().createCompositeFrame(compositeFrame);
    }

    /**
     * @todo: Consider adding a paramter for default codespace (avinor), and not use the avinor name from config
     */
    public JAXBElement<ResourceFrame> createResourceFrame(String airlineIATA) {
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

        Operator operator = resolveOperatorFromIATA(airlineIATA);
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
        Network network = objectFactory().createNetwork()
                .withVersion("1")
                .withChanged(ZonedDateTime.now())
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
                //.withDestinationDisplays() // do we need this?
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

    public List<StopPlace> createStopPlaces(ScheduledDirectFlight directFlight) {
        StopPlace departureStopPlace = objectFactory().createStopPlace()
                .withVersion("1")
                .withId("NHR:StopArea:03011537") // @todo: retrieve the actual stopplace id from NHR
                .withName(createMultilingualString(directFlight.getDepartureAirportName()))
                .withShortName(createMultilingualString(directFlight.getDepartureAirportIATA()))
                // .withQuays() // @todo: consider adding quays to stopplace, refering to gates in aviation, if available
                .withTransportMode(VehicleModeEnumeration.AIR)
                .withStopPlaceType(StopTypeEnumeration.AIRPORT);
        StopPlace arrivalStopPlace = objectFactory().createStopPlace()
                .withVersion("1")
                .withId("NHR:StopArea:03011521")  // @todo: retrieve the actual stopplace id from NHR
                .withName(createMultilingualString(directFlight.getArrivalAirportName()))
                .withShortName(createMultilingualString(directFlight.getArrivalAirportIATA()))
                // .withQuays() // @todo: consider adding quays to stopplace, refering to gates in aviation, if available
                .withTransportMode(VehicleModeEnumeration.AIR)
                .withStopPlaceType(StopTypeEnumeration.AIRPORT);
        return Lists.newArrayList(departureStopPlace, arrivalStopPlace);
    }

    public List<StopPlace> createStopPlaces(List<ScheduledStopover> scheduledStopovers) {
        List<StopPlace> stopPlaces = new ArrayList<>(scheduledStopovers.size());
        scheduledStopovers.forEach(scheduledStopover -> {
            StopPlace stopPlace = objectFactory().createStopPlace()
                    .withVersion("1")
                    .withId("NHR:StopArea:03011521")  // @todo: retrieve the actual stopplace id from NHR
                    .withName(createMultilingualString(scheduledStopover.getAirportName()))
                    .withShortName(createMultilingualString(scheduledStopover.getAirportIATA()))
                    // .withQuays() // @todo: consider adding quays to stopplace, refering to gates in aviation, if available
                    .withTransportMode(VehicleModeEnumeration.AIR)
                    .withStopPlaceType(StopTypeEnumeration.AIRPORT);
            stopPlaces.add(stopPlace);
        });
        return stopPlaces;
    }

    public JAXBElement<AvailabilityCondition> createAvailabilityCondition(LocalDate dateOfOperation, Boolean isAvailable) {
        AvailabilityCondition availabilityCondition = objectFactory().createAvailabilityCondition()
                .withVersion("any")
                .withId(String.format("%s:AvailabilityCondition:1", getAvinorConfig().getId()))
                .withFromDate(ZonedDateTime.of(dateOfOperation, LocalTime.MIN, ZoneId.of("Z")))
                .withToDate(ZonedDateTime.of(dateOfOperation, LocalTime.MIN, ZoneId.of("Z")))
                .withIsAvailable(isAvailable);
        return objectFactory().createAvailabilityCondition(availabilityCondition);
    }

    // @todo: remove all Call references and move for alighting to other top structure (see mail from Kristian)
    public List<ServiceJourney> createServiceJourneyList(ScheduledDirectFlight directFlight, List<DayType> dayTypes,
                                                         ServicePattern servicePattern, Line line, List<ScheduledStopPoint> scheduledStopPoints) {
        List<ServiceJourney> serviceJourneyList = new ArrayList<>();
        Calls_RelStructure callsRelStructure = objectFactory().createCalls_RelStructure();
        TimetabledPassingTimes_RelStructure passingTimesRelStructure = objectFactory().createTimetabledPassingTimes_RelStructure();
        List<StopPointInJourneyPattern> stopPointInJourneyPatternList = servicePattern.getPointsInSequence().getStopPointInJourneyPattern();

        DayTypeRefs_RelStructure dayTypeStructure = objectFactory().createDayTypeRefs_RelStructure();
        dayTypes.forEach(dayType -> {
            DayTypeRefStructure dayTypeRef = objectFactory().createDayTypeRefStructure().withRef(dayType.getId());
            dayTypeStructure.getDayTypeRef().add(objectFactory().createDayTypeRef(dayTypeRef));
        });

        // --- Departure Call ---
        ScheduledStopPointRefStructure departureStopPoint = objectFactory().createScheduledStopPointRefStructure()
                .withVersion("1")
                .withRef(scheduledStopPoints.get(0).getId());
        Call departureCall = objectFactory().createCall()
                .withScheduledStopPointRef(objectFactory().createScheduledStopPointRef(departureStopPoint))
                .withArrival(objectFactory().createArrivalStructure().withForAlighting(Boolean.FALSE))
                .withDeparture(objectFactory().createDepartureStructure().withTime(directFlight.getTimeOfDeparture()));
        callsRelStructure.withCallOrDatedCall(departureCall);

        // --- Departure TimetabledPassingTime ---
        StopPointInJourneyPatternRefStructure departureStopPointInJourneyPattern = objectFactory().createStopPointInJourneyPatternRefStructure()
                .withVersion("any")
                .withRef(stopPointInJourneyPatternList.get(0).getId());
        TimetabledPassingTime departurePassingTime = objectFactory().createTimetabledPassingTime()
                .withPointInJourneyPatternRef(objectFactory().createStopPointInJourneyPatternRef(departureStopPointInJourneyPattern))
                .withDepartureTime(directFlight.getTimeOfDeparture());
        passingTimesRelStructure.withTimetabledPassingTime(departurePassingTime);

        // --- Arrival Call ---
        ScheduledStopPointRefStructure arrivalStopPoint = objectFactory().createScheduledStopPointRefStructure()
                .withVersion("1")
                .withRef(scheduledStopPoints.get(1).getId());
        Call arrivalCall = objectFactory().createCall()
                .withScheduledStopPointRef(objectFactory().createScheduledStopPointRef(arrivalStopPoint))
                .withArrival(objectFactory().createArrivalStructure().withTime(directFlight.getTimeOfArrival()))
                .withDeparture(objectFactory().createDepartureStructure().withForBoarding(Boolean.FALSE));
        callsRelStructure.withCallOrDatedCall(arrivalCall);

        // --- Arrival TimetabledPassingTime ---
        StopPointInJourneyPatternRefStructure arrivalStopPointInJourneyPattern = objectFactory().createStopPointInJourneyPatternRefStructure()
                .withVersion("any")
                .withRef(stopPointInJourneyPatternList.get(1).getId());
        TimetabledPassingTime arrivalPassingTime = objectFactory().createTimetabledPassingTime()
                .withPointInJourneyPatternRef(objectFactory().createStopPointInJourneyPatternRef(arrivalStopPointInJourneyPattern))
                .withDepartureTime(directFlight.getTimeOfArrival());
        passingTimesRelStructure.withTimetabledPassingTime(arrivalPassingTime);

        ServiceJourney datedServiceJourney = objectFactory().createServiceJourney()
                .withVersion("any")
                .withId(String.format("%s:ServiceJourney:%s", getAvinorConfig().getId(), directFlight.getAirlineFlightId()))
                .withDepartureTime(directFlight.getTimeOfDeparture())
                .withDayTypes(dayTypeStructure)
                //.withJourneyPatternRef() @todo: implement!
                .withLineRef(objectFactory().createLineRef(objectFactory().createLineRefStructure().withRef(line.getId())))
                .withPassingTimes(passingTimesRelStructure);
                //.withCalls(callsRelStructure); // @todo: remove in favour of passing times
        serviceJourneyList.add(datedServiceJourney);
        return serviceJourneyList;
    }

    public List<ServiceJourney> createServiceJourneyList(ScheduledStopoverFlight stopoverFlight, ServicePattern servicePattern,
                                                              Line line, List<ScheduledStopPoint> scheduledStopPoints) {
        List<ServiceJourney> serviceJourneyList = new ArrayList<>();
        Calls_RelStructure callsRelStructure = objectFactory().createCalls_RelStructure();
        TimetabledPassingTimes_RelStructure passingTimesRelStructure = objectFactory().createTimetabledPassingTimes_RelStructure();
        List<StopPointInJourneyPattern> journeyPatternStopPoints = servicePattern.getPointsInSequence().getStopPointInJourneyPattern();

        // -- iterating to create calls
        Iterator<ScheduledStopover> stopoversiterator = stopoverFlight.getScheduledStopovers().iterator();
        Iterator<ScheduledStopPoint> stopPointsIterator = scheduledStopPoints.iterator();
        while (stopoversiterator.hasNext() && stopPointsIterator.hasNext()) {
            ScheduledStopover scheduledStopover = stopoversiterator.next();
            ScheduledStopPoint scheduledStopPoint = stopPointsIterator.next();
            ScheduledStopPointRefStructure stopPointRef = objectFactory().createScheduledStopPointRefStructure()
                    .withVersion("1")
                    .withRef(scheduledStopPoint.getId());
            ArrivalStructure arrivalStructure = objectFactory().createArrivalStructure();
            if (scheduledStopover.getArrivalTime() != null) {
                arrivalStructure.setTime(scheduledStopover.getArrivalTime());
            } else {
                arrivalStructure.setForAlighting(Boolean.FALSE); // boarding only, this is the first call
            }
            DepartureStructure departureStructure = objectFactory().createDepartureStructure();
            if (scheduledStopover.getDepartureTime() != null) {
                departureStructure.setTime(scheduledStopover.getDepartureTime());
            } else {
                departureStructure.setForBoarding(Boolean.FALSE); // alighting only, this is the last call
            }
            Call call = objectFactory().createCall()
                    .withScheduledStopPointRef(objectFactory().createScheduledStopPointRef(stopPointRef))
                    .withArrival(arrivalStructure)
                    .withDeparture(departureStructure);
            callsRelStructure.withCallOrDatedCall(call);
        }

        // -- iterating to create passing times
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
                .withId(String.format("%s:ServiceJourney:%s", getAvinorConfig().getId(), stopoverFlight.getFlightId()))
                .withDepartureTime(stopoverFlight.getScheduledStopovers().get(0).getDepartureTime())
                //.withDayTypes() @todo: implement!
                //.withJourneyPatternRef() @todo: implement!
                .withLineRef(objectFactory().createLineRef(objectFactory().createLineRefStructure().withRef(line.getId())))
                .withPassingTimes(passingTimesRelStructure);
                //.withCalls(callsRelStructure); // @todo: remove in favour of passing times
        serviceJourneyList.add(datedServiceJourney);
        return serviceJourneyList;
    }

    public List<RoutePoint> createRoutePoints(List<ScheduledStopPoint> scheduledStopPoints, String flightId) {
        List<RoutePoint> routePoints = new ArrayList<>();
        scheduledStopPoints.forEach(stopPoint -> {
            PointRefStructure pointRefStructure = objectFactory().createPointRefStructure()
                    .withVersion("1")
                    .withRef(stopPoint.getId());
            PointProjection pointProjection = objectFactory().createPointProjection()
                    .withVersion("any")
                    .withId(String.format("%s:PointProjection:%s101A0A0061101001", getAvinorConfig().getId(), flightId)) // @todo: generate postfix id in a serie
                    .withProjectedPointRef(pointRefStructure);
            Projections_RelStructure projections = objectFactory().createProjections_RelStructure()
                    .withProjectionRefOrProjection(objectFactory().createPointProjection(pointProjection));
            RoutePoint routePoint = objectFactory().createRoutePoint()
                    .withVersion("1")
                    .withId(String.format("%s:RoutePoint:%s101A0A0061101001", getAvinorConfig().getId(), flightId)) // @todo: generate postfix id in a serie
                    .withProjections(projections);
            routePoints.add(routePoint);
        });
        return routePoints;
    }

    public Route createRoute(List<RoutePoint> routePoints, String flightId, String routePath, Direction direction) {
        PointsOnRoute_RelStructure pointsOnRoute = objectFactory().createPointsOnRoute_RelStructure();
        routePoints.forEach(routePoint -> {
            RoutePointRefStructure routePointReference = objectFactory().createRoutePointRefStructure()
                    .withVersion("1")
                    .withRef(routePoint.getId());
            PointOnRoute pointOnRoute = objectFactory().createPointOnRoute()
                    .withVersion("any")
                    .withId(String.format("%s:PointOnRoute:%s101001-0", getAvinorConfig().getId(), flightId)) // @todo: fix generation of serial numbers
                    .withPointRef(objectFactory().createRoutePointRef(routePointReference));
            pointsOnRoute.getPointOnRoute().add(pointOnRoute);
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

    public RoutesInFrame_RelStructure createRoutes(ScheduledStopoverFlight stopoverFlight) {
        RoutesInFrame_RelStructure routesInFrame = objectFactory().createRoutesInFrame_RelStructure();
        List<ScheduledStopover> scheduledStopovers = stopoverFlight.getScheduledStopovers();
        PointsOnRoute_RelStructure pointsOnRoute = objectFactory().createPointsOnRoute_RelStructure();
        scheduledStopovers.forEach(stopover -> {
            RoutePointRefStructure routePointReference = objectFactory().createRoutePointRefStructure()
                    .withVersion("1")
                    .withRef("AVI:RoutePoint:0061101A0A0061101001"); // @todo: fix real ref. here
            PointOnRoute pointOnRoute = objectFactory().createPointOnRoute()
                    .withVersion("any")
                    .withId("AVI:PointOnRoute:0061101001-0")
                    .withPointRef(objectFactory().createRoutePointRef(routePointReference));
            pointsOnRoute.getPointOnRoute().add(pointOnRoute);
        });
        Route route = objectFactory().createRoute()
                .withVersion("1")
                .withId("AVI:Route:0061101")
                .withName(createMultilingualString("WF149"))
                .withPointsInSequence(pointsOnRoute); // @todo: make dynamic
                //.withDirectionRef();
        routesInFrame.getRoute_().add(objectFactory().createRoute(route));
        return routesInFrame;
    }

    private Line createLine(Route route, String flightId, String routePath) {
        RouteRefStructure routeRefStructure = objectFactory().createRouteRefStructure()
                .withVersion("1")
                .withRef(route.getId());
        RouteRefs_RelStructure routeRefs = objectFactory().createRouteRefs_RelStructure()
                .withRouteRef(routeRefStructure);
        return objectFactory().createLine()
                .withVersion("any")
                .withId(String.format("%s:Line:%s", getAvinorConfig().getId(), flightId))
                .withName(createMultilingualString(routePath))
                .withTransportMode(AllVehicleModesOfTransportEnumeration.AIR)
                .withPublicCode(flightId)
                .withRoutes(routeRefs);
    }

    private List<ScheduledStopPoint> createScheduledStopPoints(ScheduledDirectFlight directFlight) {
        ScheduledStopPoint scheduledDepartureStopPoint = objectFactory().createScheduledStopPoint()
                .withVersion("1")
                .withId(String.format("%s:StopPoint:%s101001", getAvinorConfig().getId(), directFlight.getAirlineFlightId()))
                .withName(createMultilingualString(directFlight.getDepartureAirportName()));
        ScheduledStopPoint scheduledArrivalStopPoint = objectFactory().createScheduledStopPoint()
                .withVersion("1")
                .withId(String.format("%s:StopPoint:%s101002", getAvinorConfig().getId(), directFlight.getAirlineFlightId()))
                .withName(createMultilingualString(directFlight.getArrivalAirportName()));
        return Lists.newArrayList(scheduledDepartureStopPoint, scheduledArrivalStopPoint);
    }

    private List<ScheduledStopPoint> createScheduledStopPoints(List<ScheduledStopover> scheduledStopovers, String flightId) {
        List<ScheduledStopPoint> scheduledStopPoints = new ArrayList<>(scheduledStopovers.size());
        scheduledStopovers.forEach(stopover -> {
            ScheduledStopPoint scheduledStopPoint = objectFactory().createScheduledStopPoint()
                    .withVersion("1")
                    .withId(String.format("%s:StopPoint:%s101001", getAvinorConfig().getId(), flightId))
                    .withName(createMultilingualString(stopover.getAirportName()));
            scheduledStopPoints.add(scheduledStopPoint);
        });
        return scheduledStopPoints;
    }

    public ServicePattern createServicePattern(String flightId, String routePath, Route route, List<ScheduledStopPoint> scheduledStopPoints) {
        StopPointsInJourneyPattern_RelStructure stopPointsInJourneyPattern = objectFactory().createStopPointsInJourneyPattern_RelStructure();
        scheduledStopPoints.forEach(stopPoint -> {
            ScheduledStopPointRefStructure scheduledStopPointRefStructure = objectFactory().createScheduledStopPointRefStructure()
                    .withVersion("1")
                    .withRef(String.format("%s:StopPoint:%s101001", getAvinorConfig().getId(), flightId)); // @todo: fix id generator
            StopPointInJourneyPattern stopPointInJourneyPattern = objectFactory().createStopPointInJourneyPattern()
                    .withVersion("1")
                    .withId(String.format("%s:StopPointInJourneyPattern:%s101001", getAvinorConfig().getId(), flightId)) // @todo: fix some id generator-counter here...
                    .withOrder(BigInteger.ONE) // @todo: fix a counter
                    .withScheduledStopPointRef(objectFactory().createScheduledStopPointRef(scheduledStopPointRefStructure));
            stopPointsInJourneyPattern.getStopPointInJourneyPattern().add(stopPointInJourneyPattern);
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
        Iterator<ScheduledStopPoint> stopPointsIterator = scheduledStopPoints.iterator();
        Iterator<StopPlace> stopPlacesIterator = stopPlaces.iterator();
        while (stopPointsIterator.hasNext() && stopPlacesIterator.hasNext()) {
            ScheduledStopPointRefStructure scheduledStopPointRef = objectFactory().createScheduledStopPointRefStructure()
                    .withVersion("1")
                    .withRef(stopPointsIterator.next().getId());
            StopPlaceRefStructure stopPlaceRef = objectFactory().createStopPlaceRefStructure()
                    .withVersion("1")
                    .withRef(stopPlacesIterator.next().getId());
            //QuayRefStructure quayRefStructure = new QuayRefStructure(); // do we need quays/gates?
            PassengerStopAssignment passengerStopAssignment = objectFactory().createPassengerStopAssignment()
                    .withVersion("any")
                    .withOrder(BigInteger.ONE) // @todo: fix the order count here...
                    .withId(String.format("%s:PassengerStopAssignment:%s101001", getAvinorConfig().getId(), flightId)) // @todo: fix the id generation
                    .withScheduledStopPointRef(scheduledStopPointRef)
                    .withStopPlaceRef(stopPlaceRef);
            stopAssignments.add(passengerStopAssignment);
        }
        return stopAssignments;
    }

    public Direction createDirection(String flightId) {
        return objectFactory().createDirection()
                .withId(String.format("%s:Route:%s101:Direction", getAvinorConfig().getId(), flightId)) // @todo: change id to 3-part pattern AVI:Direction:id
                .withName(createMultilingualString("Outbound"))
                .withDirectionType(DirectionTypeEnumeration.OUTBOUND);
    }

    public MultilingualString createMultilingualString(String value) {
        return objectFactory().createMultilingualString()
                //.withLang("no")
                .withValue(value);
    }

    public Duration createDuration(String lexicalRepresentation) {
        try {
            return DatatypeFactory.newInstance().newDuration(215081000L);
        } catch (DatatypeConfigurationException ex) {
            // @todo: do some logging here...
        }
        return null;
    }

    public Operator resolveOperatorFromIATA(String airlineIATA) {
        if (airlineIATA.equalsIgnoreCase(AirlineIATA.SK.name())) {
            return createSASOperator();
        }
/*
        if (airlineIATA.equalsIgnoreCase(AirlineIATA.DY.name())) {
            return createNorwegianOperator();
        }
*/
        if (airlineIATA.equalsIgnoreCase(AirlineIATA.WF.name())) {
            return createWideroeOperator();
        }
        return null;
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
                getAvinorConfig().getUrl(), getAvinorConfig().getDetails(), OrganisationTypeEnumeration.AUTHORITY);
        return objectFactory().createAuthority()
                .withVersion("1")
                .withId(String.format("%s:Company:1", getAvinorConfig().getId()))
                .withRest(organisationRest);
    }

    public Operator createSASOperator() {
        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                getSasConfig().getCompanyNumber(), getSasConfig().getName(),
                getSasConfig().getLegalName(), getSasConfig().getPhone(),
                getSasConfig().getUrl(), getSasConfig().getDetails(), OrganisationTypeEnumeration.OPERATOR);
        return objectFactory().createOperator()
                .withVersion("1")
                .withId(String.format("%s:Company:2", getSasConfig().getName()))
                .withRest(operatorRest);
    }

    public Operator createWideroeOperator() {
        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                getWideroeConfig().getCompanyNumber(), getWideroeConfig().getName(),
                getWideroeConfig().getLegalName(), getWideroeConfig().getPhone(),
                getWideroeConfig().getUrl(), getWideroeConfig().getDetails(), OrganisationTypeEnumeration.OPERATOR);
        return objectFactory().createOperator()
                .withVersion("1")
                .withId(String.format("%s:Company:2", getWideroeConfig().getName()))
                .withRest(operatorRest);
    }

    public Operator createNorwegianOperator() {
        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                getNorwegianConfig().getCompanyNumber(), getNorwegianConfig().getName(),
                getNorwegianConfig().getLegalName(), getNorwegianConfig().getPhone(),
                getNorwegianConfig().getUrl(), getNorwegianConfig().getDetails(), OrganisationTypeEnumeration.OPERATOR);
        return objectFactory().createOperator()
                .withVersion("1")
                .withId(String.format("%s:Company:2", getNorwegianConfig().getName()))
                .withRest(operatorRest);
    }

    public List<JAXBElement<?>> createOrganisationRest(String companyNumber, String name, String legalName,
                                                       String phone, String url, String details, OrganisationTypeEnumeration organisationType) {
        JAXBElement<String> companyNumberStructure = objectFactory()
                .createOrganisation_VersionStructureCompanyNumber(companyNumber);
        JAXBElement<MultilingualString> nameStructure = objectFactory()
                .createOrganisation_VersionStructureName(createMultilingualString(name));
        JAXBElement<MultilingualString> legalNameStructure = objectFactory()
                .createOrganisation_VersionStructureLegalName(createMultilingualString(legalName));
        JAXBElement<List<OrganisationTypeEnumeration>> organisationTypes = objectFactory()
                .createOrganisation_VersionStructureOrganisationType(Collections.singletonList(organisationType));
        JAXBElement<ContactStructure> contactStructure = createContactStructure(phone, url, details);
        return Lists.newArrayList(companyNumberStructure, nameStructure, legalNameStructure, organisationTypes, contactStructure);
    }

    public JAXBElement<ContactStructure> createContactStructure(String phone, String url, String furtherDetails) {
        ContactStructure contactStructure = objectFactory().createContactStructure()
                .withPhone(phone)
                .withUrl(url)
                .withFurtherDetails(createMultilingualString(furtherDetails));
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
