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
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component(value = "scheduledFlightToNetexConverter")
public class ScheduledFlightToNetexConverter {

    private AvinorAuthorityConfig avinorConfig;
    private NhrAuthorityConfig nhrConfig;
    private SasOperatorConfig sasConfig;
    private WideroeOperatorConfig wideroeConfig;
    private NorwegianOperatorConfig norwegianConfig;

    public JAXBElement<PublicationDeliveryStructure> convertToNetex(ScheduledDirectFlight directFlight) {
        String routePath = String.format("%s-%s", directFlight.getDepartureAirportIATA(), directFlight.getArrivalAirportIATA());
        String flightId = directFlight.getAirlineFlightId();

        Direction direction = createDirection(flightId);
        List<ScheduledStopPoint> scheduledStopPoints = createScheduledStopPoints(directFlight);
        List<RoutePoint> routePoints = createRoutePoints(scheduledStopPoints, flightId);
        Route route = createRoute(routePoints, flightId, routePath, direction);
        Line line = createLine(route, flightId, routePath);
        ServicePattern servicePattern = createServicePattern(flightId, routePath, route, scheduledStopPoints);

        Frames_RelStructure frames = new Frames_RelStructure();
        frames.getCommonFrame().add(createResourceFrame(directFlight.getAirlineIATA()));
        frames.getCommonFrame().add(createSiteFrame(directFlight));
        frames.getCommonFrame().add(createServiceFrame(direction, flightId, routePoints, route, line, scheduledStopPoints, servicePattern));
        //framesRelStructure.getCommonFrame().add(createServiceCalendarFrame());
        //framesRelStructure.getCommonFrame().add(createTimetableFrame());

        JAXBElement<CompositeFrame> compositeFrame = createCompositeFrame(flightId, frames);
        PublicationDeliveryStructure publicationDeliveryStructure = createPublicationDeliveryStructure(compositeFrame, flightId, routePath);
        return objectFactory().createPublicationDelivery(publicationDeliveryStructure);
    }

    public PublicationDeliveryStructure convertToNetex(ScheduledStopoverFlight stopoverFlight) {
        return null;
    }

    public PublicationDeliveryStructure createPublicationDeliveryStructure(
            JAXBElement<CompositeFrame> compositeFrame, String flightId, String routePath) {
        DataObjects dataObjects = new DataObjects();
        dataObjects.getCompositeFrameOrCommonFrame().add(compositeFrame);

        return new PublicationDeliveryStructure()
                .withVersion("1.0")
                .withPublicationTimestamp(ZonedDateTime.now())
                .withParticipantRef(getAvinorConfig().getId()) // should this be Avinor or the actual flight airline?
                .withDescription(createMultilingualString(String.format("Flight %s - %s", flightId, routePath)))
                .withDataObjects(dataObjects);
    }

    public JAXBElement<CompositeFrame> createCompositeFrame(String flightId, Frames_RelStructure frames) {
        Codespaces_RelStructure codespaces = new Codespaces_RelStructure()
                .withCodespaceRefOrCodespace(
                        Arrays.asList(avinorCodespace(), nhrCodespace()));

        CompositeFrame compositeFrame = new CompositeFrame()
                .withVersion("1")
                .withCreated(ZonedDateTime.now())
                .withId(String.format("%s:Norway:CompositeFrame:%s", getAvinorConfig().getId(), flightId))
                .withCodespaces(codespaces)
                .withFrames(frames);
        return objectFactory().createCompositeFrame(compositeFrame);
    }

    public JAXBElement<ResourceFrame> createResourceFrame(String airlineIATA) {
        CodespaceRefStructure codespaceRefStructure = new CodespaceRefStructure()
                .withRef(avinorCodespace().getId());

        LocaleStructure localeStructure = new LocaleStructure()
                .withTimeZone("CET")
                .withSummerTimeZone("CEST")
                .withDefaultLanguage("no");

        VersionFrameDefaultsStructure versionFrameDefaultsStructure = new VersionFrameDefaultsStructure()
                .withDefaultCodespaceRef(codespaceRefStructure)
                .withDefaultLocale(localeStructure);

        OrganisationsInFrame_RelStructure organisationsInFrame = new OrganisationsInFrame_RelStructure();
        organisationsInFrame.getOrganisation_().add(objectFactory().createAuthority(createAuthority()));

        Operator operator = resolveOperatorFromIATA(airlineIATA);
        organisationsInFrame.getOrganisation_().add(objectFactory().createOperator(operator));

        ResourceFrame resourceFrame = new ResourceFrame()
                .withVersion("any")
                .withId(String.format("%s:ResourceFrame:RF1", getAvinorConfig().getId()))
                .withFrameDefaults(versionFrameDefaultsStructure)
                .withOrganisations(organisationsInFrame);
        return objectFactory().createResourceFrame(resourceFrame);
    }

    public JAXBElement<SiteFrame> createSiteFrame(ScheduledDirectFlight directFlight) {
        List<StopPlace> stopPlaces = new ArrayList<>();
        StopPlace departureStopPlace = new StopPlace()
                .withVersion("1")
                .withId("NHR:StopArea:03011537") // @todo: retrieve the actual stopplace id from NHR
                .withName(createMultilingualString(directFlight.getDepartureAirportIATA())) // @todo: change to airportname when available
                .withShortName(createMultilingualString(directFlight.getDepartureAirportIATA()))
                // .withQuays() // @todo: consider adding quays to stopplace, refering to gates in aviation, if available
                .withTransportMode(VehicleModeEnumeration.AIR)
                .withStopPlaceType(StopTypeEnumeration.AIRPORT);
        stopPlaces.add(departureStopPlace);

        StopPlace arrivalStopPlace = new StopPlace()
                .withVersion("1")
                .withId("NHR:StopArea:03011521")  // @todo: retrieve the actual stopplace id from NHR
                .withName(createMultilingualString(directFlight.getArrivalAirportIATA())) // @todo: change to airportname when available
                .withShortName(createMultilingualString(directFlight.getArrivalAirportIATA()))
                // .withQuays() // @todo: consider adding quays to stopplace, refering to gates in aviation, if available
                .withTransportMode(VehicleModeEnumeration.AIR)
                .withStopPlaceType(StopTypeEnumeration.AIRPORT);
        stopPlaces.add(arrivalStopPlace);

        StopPlacesInFrame_RelStructure stopPlacesInFrameRelStructure =
                new StopPlacesInFrame_RelStructure()
                        .withStopPlace(stopPlaces);

        SiteFrame siteFrame = new SiteFrame()
                .withVersion("any")
                .withId(String.format("%s:SiteFrame:SF01", getAvinorConfig().getId()))
                .withStopPlaces(stopPlacesInFrameRelStructure);
        return objectFactory().createSiteFrame(siteFrame);
    }

    public JAXBElement<SiteFrame> createSiteFrame(List<ScheduledStopover> stopovers) {
        List<StopPlace> stopPlaces = new ArrayList<>();
        stopovers.forEach(stopover -> {
            StopPlace stopPlace = new StopPlace()
                    .withVersion("1")
                    .withId("NHR:StopArea:03011537") // @todo: make dynamic
                    .withName(createMultilingualString(stopover.getAirportIATA())) // @todo: change to airportname when available
                    .withShortName(createMultilingualString(stopover.getAirportIATA()))
                    // .withQuays() // @todo: consider adding quays to stopplace, refering to gates in aviation, if available
                    .withTransportMode(VehicleModeEnumeration.AIR)
                    .withStopPlaceType(StopTypeEnumeration.AIRPORT);
            stopPlaces.add(stopPlace);
        });

        StopPlacesInFrame_RelStructure stopPlacesInFrameRelStructure =
                new StopPlacesInFrame_RelStructure()
                        .withStopPlace(stopPlaces);

        SiteFrame siteFrame = new SiteFrame()
                .withVersion("any")
                .withId("AVI:SiteFrame:SF01") // @todo: make dynamic
                .withStopPlaces(stopPlacesInFrameRelStructure);
        return objectFactory().createSiteFrame(siteFrame);
    }

    public JAXBElement<ServiceFrame> createServiceFrame(Direction direction, String flightId, List<RoutePoint> routePoints,
                                                        Route route, Line line, List<ScheduledStopPoint> scheduledStopPoints,
                                                        ServicePattern servicePattern) {
        Network network = new Network()
                .withVersion("1")
                .withChanged(ZonedDateTime.now())
                .withId(String.format("%s:GroupOfLine:%s", getAvinorConfig().getId(), getAvinorConfig().getName()))
                .withName(createMultilingualString(getAvinorConfig().getName()));

        DirectionsInFrame_RelStructure directionsInFrame = new DirectionsInFrame_RelStructure()
                .withDirection(direction);

        RoutePointsInFrame_RelStructure routePointsInFrame = new RoutePointsInFrame_RelStructure()
                .withRoutePoint(routePoints);

        RoutesInFrame_RelStructure routesInFrame = new RoutesInFrame_RelStructure();
        routesInFrame.getRoute_().add(objectFactory().createRoute(route));

        LinesInFrame_RelStructure linesInFrame = new LinesInFrame_RelStructure();
        linesInFrame.getLine_().add(objectFactory().createLine(line));

        ScheduledStopPointsInFrame_RelStructure scheduledStopPointsInFrame = new ScheduledStopPointsInFrame_RelStructure()
                .withScheduledStopPoint(scheduledStopPoints);

        ServicePatternsInFrame_RelStructure servicePatternsInFrame = new ServicePatternsInFrame_RelStructure()
                .withServicePatternOrJourneyPatternView(servicePattern);

        ServiceFrame serviceFrame = new ServiceFrame()
                .withVersion("any")
                .withId(String.format("%s:ServiceFrame:%s", getAvinorConfig().getId(), flightId))
                .withNetwork(network)
                .withDirections(directionsInFrame)
                .withRoutePoints(routePointsInFrame)
                .withRoutes(routesInFrame)
                .withLines(linesInFrame)
                //.withDestinationDisplays() // do we need this?
                .withScheduledStopPoints(scheduledStopPointsInFrame)
                .withServicePatterns(servicePatternsInFrame);
                //.withStopAssignments(createStopAssignments(null)); // @todo: change null argument to real collection
        return objectFactory().createServiceFrame(serviceFrame);
    }

    public JAXBElement<ServiceCalendarFrame> createServiceCalendarFrame() {
        ServiceCalendarFrame serviceCalendarFrame = new ServiceCalendarFrame();
        return objectFactory().createServiceCalendarFrame(serviceCalendarFrame);
    }

    public JAXBElement<TimetableFrame> createTimetableFrame() {
        // @todo: retrieve the from-to dates as used in original request, i.e. as headers
        ValidityConditions_RelStructure validityConditionsRelStructure = new ValidityConditions_RelStructure()
                .withValidityConditionRefOrValidBetweenOrValidityCondition_(createAvailabilityCondition());

        // @todo: consider the type of journey to use, ServiceJourney, TemplateServiceJourney, or DatedServiceJourney
        JourneysInFrame_RelStructure journeysInFrameRelStructure = new JourneysInFrame_RelStructure();
        //journeysInFrameRelStructure.getDatedServiceJourneyOrDeadRunOrServiceJourney().addAll(serviceJourneyList);

        TimetableFrame timetableFrame = new TimetableFrame()
                .withVersion("1")
                .withId(String.format("%s:TimetableFrame:TF01", getAvinorConfig().getId()))
                .withValidityConditions(validityConditionsRelStructure)
                .withName(createMultilingualString("Rute for 3 dager frem i tid"))
                .withVehicleModes(VehicleModeEnumeration.AIR)
                .withVehicleJourneys(journeysInFrameRelStructure);
        return objectFactory().createTimetableFrame(timetableFrame);
    }

    public AvailabilityCondition createAvailabilityCondition() {
        return new AvailabilityCondition()
                .withVersion("any")
                .withId(String.format("%s:AvailabilityCondition:1", getAvinorConfig().getId()))
                .withDescription(createMultilingualString("Flyruter fra idag og tre dager fremover"))
                .withFromDate(ZonedDateTime.now())
                .withToDate(ZonedDateTime.now().plusDays(3L));
    }

    public List<DatedServiceJourney> createServiceJourneyList(ScheduledStopoverFlight stopoverFlight) {
        List<DatedServiceJourney> serviceJourneyList = new ArrayList<>();
        List<ScheduledStopover> scheduledStopovers = stopoverFlight.getScheduledStopovers();
        Calls_RelStructure callsRelStructure = new Calls_RelStructure();
        scheduledStopovers.forEach(stopover -> {
            // @todo: make dynamic
            ScheduledStopPointRefStructure scheduledStopPointReference = new ScheduledStopPointRefStructure()
                    .withVersion("1")
                    .withRef("AVI:StopPoint:0061101001");
            JAXBElement<ScheduledStopPointRefStructure> scheduledStopPointRef = objectFactory().createScheduledStopPointRef(scheduledStopPointReference);
            ArrivalStructure arrivalStructure = new ArrivalStructure();
            if (stopover.getArrivalTime() != null) {
                arrivalStructure.setTime(stopover.getArrivalTime());
            } else {
                arrivalStructure.setForAlighting(Boolean.FALSE); // boarding only, this is the first call
            }
            DepartureStructure departureStructure = new DepartureStructure();
            if (stopover.getDepartureTime() != null) {
                departureStructure.setTime(stopover.getDepartureTime());
            } else {
                departureStructure.setForBoarding(Boolean.FALSE); // alighting only, this is the last call
            }
            Call call = new Call()
                    .withScheduledStopPointRef(scheduledStopPointRef)
                    .withArrival(arrivalStructure)
                    .withDeparture(departureStructure);
            callsRelStructure.withCallOrDatedCall(call);
        });
        DatedServiceJourney datedServiceJourney = new DatedServiceJourney()
                .withVersion("any")
                .withId("AVI:DatedServiceJourney:WF149") // @todo: make dynamic
                //.withDepartureTime(LocalTime.now()) // @todo: add departure time of first journey to flight
                //.withDayTypes()
                //.withLineRef();
                //.withJourneyPatternRef()
                //.withLineRef() // @todo: IMPORTANT!!
                .withCalls(callsRelStructure);
        serviceJourneyList.add(datedServiceJourney);
        return serviceJourneyList;
    }

    public List<RoutePoint> createRoutePoints(List<ScheduledStopPoint> scheduledStopPoints, String flightId) {
        List<RoutePoint> routePoints = new ArrayList<>();
        scheduledStopPoints.forEach(stopPoint -> {
            PointRefStructure pointRefStructure = new PointRefStructure()
                    .withVersion("1")
                    .withRef(stopPoint.getId());
            PointProjection pointProjection = new PointProjection()
                    .withVersion("any")
                    .withId(String.format("%s:PointProjection:%s101A0A0061101001", getAvinorConfig().getId(), flightId)) // @todo: generate postfix id in a serie
                    .withProjectedPointRef(pointRefStructure);
            Projections_RelStructure projections = new Projections_RelStructure()
                    .withProjectionRefOrProjection(objectFactory().createPointProjection(pointProjection));
            RoutePoint routePoint = new RoutePoint()
                    .withVersion("1")
                    .withId(String.format("%s:RoutePoint:%s101A0A0061101001", getAvinorConfig().getId(), flightId)) // @todo: generate postfix id in a serie
                    .withProjections(projections);
            routePoints.add(routePoint);
        });
        return routePoints;
    }

    public List<RoutePoint> createRoutePoints(ScheduledStopoverFlight stopoverFlight) {
        List<RoutePoint> routePoints = new ArrayList<>();
        List<ScheduledStopover> scheduledStopovers = stopoverFlight.getScheduledStopovers();
        scheduledStopovers.forEach(stopover -> {
            PointRefStructure pointRefStructure = new PointRefStructure()
                    .withVersion("1")
                    .withRef("AVI:StopPoint:0061101001"); // @todo: refer to an existing StopPoint here
            PointProjection pointProjection = new PointProjection()
                    .withVersion("any")
                    .withId("AVI:PointProjection:0061101A0A0061101001");
                    //.withProjectedPointRef();  // @todo: make dynamic
            Projections_RelStructure projections = new Projections_RelStructure()
                    .withProjectionRefOrProjection(objectFactory().createPointProjection(pointProjection));
            RoutePoint routePoint = new RoutePoint()
                    .withVersion("1")
                    .withId("AVI:RoutePoint:0061101A0A0061101001") // @todo: make dynamic
                    .withProjections(projections);
            routePoints.add(routePoint);
        });
        return routePoints;
    }

    public Route createRoute(List<RoutePoint> routePoints, String flightId, String routePath, Direction direction) {
        PointsOnRoute_RelStructure pointsOnRoute = new PointsOnRoute_RelStructure();
        routePoints.forEach(routePoint -> {
            RoutePointRefStructure routePointReference = new RoutePointRefStructure()
                    .withVersion("1")
                    .withRef(routePoint.getId());
            PointOnRoute pointOnRoute = new PointOnRoute()
                    .withVersion("any")
                    .withId(String.format("%s:PointOnRoute:%s101001-0", getAvinorConfig().getId(), flightId)) // @todo: fix generation of serial numbers
                    //.withOrder(BigInteger.valueOf(stopover.getOrder())); // @todo: fix support for order values or implement counter
                    .withPointRef(objectFactory().createRoutePointRef(routePointReference));
            pointsOnRoute.getPointOnRoute().add(pointOnRoute);
        });
        DirectionRefStructure directionRefStructure = new DirectionRefStructure()
                .withRef(direction.getId());
        return new Route()
                .withVersion("1")
                .withId(String.format("%s:Route:%s101", getAvinorConfig().getId(), flightId))
                .withName(createMultilingualString(String.format("%s: %s", flightId, routePath)))
                .withPointsInSequence(pointsOnRoute)
                .withDirectionRef(directionRefStructure);
    }

    public RoutesInFrame_RelStructure createRoutes(ScheduledStopoverFlight stopoverFlight) {
        RoutesInFrame_RelStructure routesInFrame = new RoutesInFrame_RelStructure();
        List<ScheduledStopover> scheduledStopovers = stopoverFlight.getScheduledStopovers();
        PointsOnRoute_RelStructure pointsOnRoute = new PointsOnRoute_RelStructure();
        scheduledStopovers.forEach(stopover -> {
            RoutePointRefStructure routePointReference = new RoutePointRefStructure()
                    .withVersion("1")
                    .withRef("AVI:RoutePoint:0061101A0A0061101001"); // @todo: fix real ref. here
            PointOnRoute pointOnRoute = new PointOnRoute()
                    .withVersion("any")
                    .withId("AVI:PointOnRoute:0061101001-0")
                    //.withOrder(BigInteger.valueOf(stopover.getOrder())); // @todo: fix support for order values or implement counter
                    .withPointRef(objectFactory().createRoutePointRef(routePointReference));
            pointsOnRoute.getPointOnRoute().add(pointOnRoute);
        });
        Route route = new Route()
                .withVersion("1")
                .withId("AVI:Route:0061101")
                .withName(createMultilingualString("WF149"))
                .withPointsInSequence(pointsOnRoute); // @todo: make dynamic
                //.withDirectionRef();
        routesInFrame.getRoute_().add(objectFactory().createRoute(route));
        return routesInFrame;
    }

    private Line createLine(Route route, String flightId, String routePath) {
        RouteRefStructure routeRefStructure = new RouteRefStructure()
                .withVersion("1")
                .withRef(route.getId());
        RouteRefs_RelStructure routeRefs = new RouteRefs_RelStructure()
                .withRouteRef(routeRefStructure);
        return new Line()
                .withVersion("any")
                .withId(String.format("%s:Line:%s", getAvinorConfig().getId(), flightId))
                .withName(createMultilingualString(routePath))
                .withTransportMode(AllVehicleModesOfTransportEnumeration.AIR)
                .withPublicCode(flightId)
                .withRoutes(routeRefs);
    }

    private LinesInFrame_RelStructure createLines() {
        LinesInFrame_RelStructure linesInFrame = new LinesInFrame_RelStructure();
        RouteRefStructure routeRefStructure = new RouteRefStructure()
                .withVersion("1")
                .withRef("AVI:Route:0061101");
        RouteRefs_RelStructure routeRefs = new RouteRefs_RelStructure()
                .withRouteRef(routeRefStructure);
        Line line = new Line()
                .withVersion("any")
                .withId("AVI:Line:WF149")
                .withName(createMultilingualString("Oslo-Bergen")) // @todo: make dynamic
                .withTransportMode(AllVehicleModesOfTransportEnumeration.AIR)
                .withPublicCode("WF149") // @todo: make dynamic
                .withRoutes(routeRefs);
        linesInFrame.getLine_().add(objectFactory().createLine(line));
        return null;
    }

    private List<ScheduledStopPoint> createScheduledStopPoints(ScheduledDirectFlight directFlight) {
        ScheduledStopPoint scheduledDepartureStopPoint = new ScheduledStopPoint()
                .withVersion("1")
                .withId(String.format("%s:StopPoint:%s101001", getAvinorConfig().getId(), directFlight.getAirlineFlightId()))
                .withName(createMultilingualString(directFlight.getDepartureAirportIATA())); // @todo: change to airport name when available
        ScheduledStopPoint scheduledArrivalStopPoint = new ScheduledStopPoint()
                .withVersion("1")
                .withId(String.format("%s:StopPoint:%s101002", getAvinorConfig().getId(), directFlight.getAirlineFlightId()))
                .withName(createMultilingualString(directFlight.getArrivalAirportIATA())); // @todo: change to airport name when available
        return Lists.newArrayList(scheduledDepartureStopPoint, scheduledArrivalStopPoint);
    }

    private ScheduledStopPointsInFrame_RelStructure createScheduledStopPoints(ScheduledStopoverFlight stopoverFlight) {
        ScheduledStopPointsInFrame_RelStructure scheduledStopPointsInFrame = new ScheduledStopPointsInFrame_RelStructure();
        List<ScheduledStopover> scheduledStopovers = stopoverFlight.getScheduledStopovers();
        scheduledStopovers.forEach(stopover -> {
            ScheduledStopPoint scheduledStopPoint = new ScheduledStopPoint()
                    .withVersion("1")
                    .withId("AVI:StopPoint:0061101001")
                    .withName(createMultilingualString(stopover.getAirportIATA())); // @todo: change to airport name when available
            scheduledStopPointsInFrame.getScheduledStopPoint().add(scheduledStopPoint);
        });
        return scheduledStopPointsInFrame;
    }

    public ServicePattern createServicePattern(String flightId, String routePath, Route route, List<ScheduledStopPoint> scheduledStopPoints) {
        StopPointsInJourneyPattern_RelStructure stopPointsInJourneyPattern = new StopPointsInJourneyPattern_RelStructure();
        scheduledStopPoints.forEach(stopPoint -> {
            ScheduledStopPointRefStructure scheduledStopPointRefStructure = new ScheduledStopPointRefStructure().withVersion("1")
                    .withRef(String.format("%s:StopPoint:%s101001", getAvinorConfig().getId(), flightId)); // @todo: fix id generator
            StopPointInJourneyPattern stopPointInJourneyPattern = new StopPointInJourneyPattern()
                    .withVersion("1")
                    .withId(String.format("%s:StopPointInJourneyPattern:%s101001", getAvinorConfig().getId(), flightId)) // @todo: fix some id generator-counter here...
                    .withOrder(BigInteger.ONE) // @todo: fix a counter
                    .withScheduledStopPointRef(objectFactory().createScheduledStopPointRef(scheduledStopPointRefStructure));
            stopPointsInJourneyPattern.getStopPointInJourneyPattern().add(stopPointInJourneyPattern);
        });
        return new ServicePattern()
                .withVersion("1")
                .withId(String.format("%s:ServicePattern:%s101", getAvinorConfig().getId(), flightId)) // @todo: fix id generator
                .withName(createMultilingualString(routePath))
                .withRouteRef(new RouteRefStructure().withVersion("1").withRef(route.getId()))
                .withPointsInSequence(stopPointsInJourneyPattern);
    }

    public ServicePatternsInFrame_RelStructure createServicePattern(List<ScheduledStopPoint> scheduledStopPoints) {
        StopPointsInJourneyPattern_RelStructure stopPointsInJourneyPattern = new StopPointsInJourneyPattern_RelStructure();
        scheduledStopPoints.forEach(stopPoint -> {
            StopPointInJourneyPattern stopPointInJourneyPattern = new StopPointInJourneyPattern()
                    .withVersion("1")
                    .withId("AVI:StopPointInJourneyPattern:0061101001")
                    .withOrder(BigInteger.ONE) // @todo: fix a counter
                    .withScheduledStopPointRef(objectFactory().createScheduledStopPointRef(new ScheduledStopPointRefStructure().withVersion("1").withRef("AVI:StopPoint:0061101001")));
            stopPointsInJourneyPattern.getStopPointInJourneyPattern().add(stopPointInJourneyPattern);
        });
        ServicePattern servicePattern = new ServicePattern()
                .withVersion("1")
                .withId("AVI:ServicePattern:0061101")
                .withName(createMultilingualString("WF149"))
                .withRouteRef(new RouteRefStructure().withVersion("1").withRef("AVI:Route:0061101"))
                .withPointsInSequence(stopPointsInJourneyPattern);
        return new ServicePatternsInFrame_RelStructure()
                .withServicePatternOrJourneyPatternView(servicePattern);
    }

    public StopAssignmentsInFrame_RelStructure createStopAssignments(List<ScheduledStopPoint> scheduledStopPoints) {
        StopAssignmentsInFrame_RelStructure stopAssignmentsInFrame = new StopAssignmentsInFrame_RelStructure();
        scheduledStopPoints.forEach(stopPoint -> {
            PassengerStopAssignment passengerStopAssignment = new PassengerStopAssignment()
                    .withVersion("any")
                    .withOrder(BigInteger.ONE)
                    .withId("AVI:PassengerStopAssignment:0061101001")
                    .withScheduledStopPointRef(new ScheduledStopPointRefStructure().withVersion("1").withRef("AVI:StopPoint:0061101001"))
                    .withStopPlaceRef(new StopPlaceRefStructure().withVersion("1").withRef("NHR:StopArea:03011521"));
            stopAssignmentsInFrame.getStopAssignment().add(objectFactory().createStopAssignment(passengerStopAssignment));
        });
        return stopAssignmentsInFrame;
    }

    public Direction createDirection(String flightId) {
        return new Direction()
                .withId(String.format("%s:Route:%s101:Direction", getAvinorConfig().getId(), flightId))
                .withName(createMultilingualString("Outbound"))
                .withDirectionType(DirectionTypeEnumeration.OUTBOUND);
    }

    public MultilingualString createMultilingualString(String value) {
        return new MultilingualString()
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
        if (airlineIATA.equalsIgnoreCase(AirlineIATA.DY.name())) {
            return createNorwegianOperator();
        }
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
        return new Codespace()
                .withId(getAvinorConfig().getName().toLowerCase())
                .withXmlns(getAvinorConfig().getId())
                .withXmlnsUrl(getAvinorConfig().getUrl());
    }

    public Codespace nhrCodespace() {
        return new Codespace()
                .withId(getNhrConfig().getId().toLowerCase())
                .withXmlns(getNhrConfig().getId())
                .withXmlnsUrl(getNhrConfig().getUrl());
    }

    public Authority createAuthority() {
        List<JAXBElement<?>> organisationRest = createOrganisationRest(
                getAvinorConfig().getCompanyNumber(), getAvinorConfig().getName(),
                getAvinorConfig().getLegalName(), getAvinorConfig().getPhone(),
                getAvinorConfig().getUrl(), getAvinorConfig().getDetails(), OrganisationTypeEnumeration.AUTHORITY);
        return new Authority()
                .withVersion("1")
                .withId(String.format("%s:Company:1", getAvinorConfig().getId()))
                .withRest(organisationRest);
    }

    public Operator createSASOperator() {
        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                getSasConfig().getCompanyNumber(), getSasConfig().getName(),
                getSasConfig().getLegalName(), getSasConfig().getPhone(),
                getSasConfig().getUrl(), getSasConfig().getDetails(), OrganisationTypeEnumeration.OPERATOR);
        return new Operator()
                .withVersion("1")
                .withId(String.format("%s:Company:2", getSasConfig().getName()))
                .withRest(operatorRest);
    }

    public Operator createWideroeOperator() {
        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                getWideroeConfig().getCompanyNumber(), getWideroeConfig().getName(),
                getWideroeConfig().getLegalName(), getWideroeConfig().getPhone(),
                getWideroeConfig().getUrl(), getWideroeConfig().getDetails(), OrganisationTypeEnumeration.OPERATOR);
        return new Operator()
                .withVersion("1")
                .withId(String.format("%s:Company:2", getWideroeConfig().getName()))
                .withRest(operatorRest);
    }

    public Operator createNorwegianOperator() {
        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                getNorwegianConfig().getCompanyNumber(), getNorwegianConfig().getName(),
                getNorwegianConfig().getLegalName(), getNorwegianConfig().getPhone(),
                getNorwegianConfig().getUrl(), getNorwegianConfig().getDetails(), OrganisationTypeEnumeration.OPERATOR);
        return new Operator()
                .withVersion("1")
                .withId(String.format("%s:Company:2", getNorwegianConfig().getName()))
                .withRest(operatorRest);
    }

    public List<JAXBElement<?>> createOrganisationRest(String companyNumber, String name, String legalName,
                                                       String phone, String url, String details, OrganisationTypeEnumeration organisationType) {
        JAXBElement<String> companyNumberStructure = objectFactory()
                .createOrganisation_VersionStructureCompanyNumber(companyNumber);
        JAXBElement<MultilingualString> nameStructure = objectFactory()
                .createOrganisation_VersionStructureName(new MultilingualString().withValue(name));
        JAXBElement<MultilingualString> legalNameStructure = objectFactory()
                .createOrganisation_VersionStructureLegalName(new MultilingualString().withValue(legalName));
        JAXBElement<List<OrganisationTypeEnumeration>> organisationTypes = objectFactory()
                .createOrganisation_VersionStructureOrganisationType(Collections.singletonList(organisationType));
        JAXBElement<ContactStructure> contactStructure = createContactStructure(phone, url, details);
        return Arrays.asList(companyNumberStructure, nameStructure, legalNameStructure, organisationTypes, contactStructure);
    }

    public JAXBElement<ContactStructure> createContactStructure(String phone, String url, String furtherDetails) {
        ContactStructure contactStructure = new ContactStructure()
                .withPhone(phone)
                .withUrl(url)
                .withFurtherDetails(new MultilingualString().withValue(furtherDetails));
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
