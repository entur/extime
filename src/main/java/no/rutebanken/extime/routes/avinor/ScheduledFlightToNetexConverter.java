package no.rutebanken.extime.routes.avinor;

import com.google.common.collect.Lists;
import no.rutebanken.extime.NetexModelConfig;
import no.rutebanken.extime.model.ScheduledDirectFlight;
import no.rutebanken.extime.model.ScheduledStopover;
import no.rutebanken.extime.model.ScheduledStopoverFlight;
import no.rutebanken.netex.model.*;
import no.rutebanken.netex.model.PublicationDeliveryStructure.DataObjects;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
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

@Component
public class ScheduledFlightToNetexConverter {

    private static final String AVINOR_ID = "AVI";
    private static final String AVINOR_NAME = "Avinor";

    private ApplicationContext context = new AnnotationConfigApplicationContext(NetexModelConfig.class);
    private ObjectFactory objectFactory = context.getBean("netexObjectFactory", ObjectFactory.class);
    private Codespace avinorCodespace = context.getBean("avinorCodespace", Codespace.class);
    private Codespace nhrCodespace = context.getBean("nhrCodespace", Codespace.class);

    public PublicationDeliveryStructure convertToNetex(ScheduledDirectFlight directFlight) {
        String routePath = String.format("%s-%s", directFlight.getDepartureAirportIATA(), directFlight.getArrivalAirportIATA());

        Direction direction = createDirection(directFlight.getAirlineFlightId());
        List<ScheduledStopPoint> scheduledStopPoints = createScheduledStopPoints(directFlight);
        List<RoutePoint> routePoints = createRoutePoints(scheduledStopPoints, directFlight.getAirlineFlightId());
        List<JAXBElement<Route>> routes = createRoutes(routePoints, directFlight.getAirlineFlightId(), routePath, direction);

        Frames_RelStructure frames = new Frames_RelStructure();
        frames.getCommonFrame().add(createResourceFrame(directFlight.getAirlineIATA()));
        frames.getCommonFrame().add(createSiteFrame(directFlight));
        frames.getCommonFrame().add(createServiceFrame(direction, scheduledStopPoints, routePoints, routes));
        //framesRelStructure.getCommonFrame().add(createServiceCalendarFrame());
        //framesRelStructure.getCommonFrame().add(createTimetableFrame());

        JAXBElement<CompositeFrame> compositeFrame = createCompositeFrame(directFlight.getAirlineFlightId(), frames);
        return createPublicationDeliveryStructure(compositeFrame, directFlight.getAirlineFlightId(), routePath);
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
                .withParticipantRef(AVINOR_ID) // should this be Avinor or the actual flight airline?
                .withDescription(createMultilingualString(String.format("Flight %s: %s", flightId, routePath)))
                .withDataObjects(dataObjects);
    }

    public JAXBElement<CompositeFrame> createCompositeFrame(String flightId, Frames_RelStructure frames) {
        Codespaces_RelStructure codespaces = new Codespaces_RelStructure()
                .withCodespaceRefOrCodespace(
                        Arrays.asList(getAvinorCodespace(), getNhrCodespace()));

        CompositeFrame compositeFrame = new CompositeFrame()
                .withVersion("1")
                .withCreated(ZonedDateTime.now())
                .withId(String.format("%s:Norway:CompositeFrame:%s", AVINOR_ID, flightId))
                .withCodespaces(codespaces)
                .withFrames(frames);
        return getObjectFactory().createCompositeFrame(compositeFrame);
    }

    public JAXBElement<ResourceFrame> createResourceFrame(String airlineIATA) {
        CodespaceRefStructure codespaceRefStructure = new CodespaceRefStructure()
                .withRef(getAvinorCodespace().getId());

        LocaleStructure localeStructure = new LocaleStructure()
                .withTimeZone("CET")
                .withSummerTimeZone("CEST")
                .withDefaultLanguage("no");

        VersionFrameDefaultsStructure versionFrameDefaultsStructure = new VersionFrameDefaultsStructure()
                .withDefaultCodespaceRef(codespaceRefStructure)
                .withDefaultLocale(localeStructure);

        OrganisationsInFrame_RelStructure organisationsInFrame = new OrganisationsInFrame_RelStructure();
        organisationsInFrame.getOrganisation_().add(createAuthority());

        Operator operator = context.getBean(airlineIATA, Operator.class);
        organisationsInFrame.getOrganisation_().add(getObjectFactory().createOperator(operator));

        ResourceFrame resourceFrame = new ResourceFrame()
                .withVersion("any")
                .withId(String.format("%s:ResourceFrame:RF1", AVINOR_ID))
                .withFrameDefaults(versionFrameDefaultsStructure)
                .withOrganisations(organisationsInFrame);
        return getObjectFactory().createResourceFrame(resourceFrame);
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
                .withId(String.format("%s:SiteFrame:SF01", AVINOR_ID))
                .withStopPlaces(stopPlacesInFrameRelStructure);
        return getObjectFactory().createSiteFrame(siteFrame);
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
        return getObjectFactory().createSiteFrame(siteFrame);
    }

    public JAXBElement<ServiceFrame> createServiceFrame(Direction direction, List<ScheduledStopPoint> scheduledStopPoints,
                                                        List<RoutePoint> routePoints, List<JAXBElement<Route>> routes) {
        Network network = new Network()
                .withVersion("1")
                .withChanged(ZonedDateTime.now())
                .withId(String.format("%s:GroupOfLine:%s", AVINOR_ID, AVINOR_NAME))
                .withName(createMultilingualString(AVINOR_NAME));

        DirectionsInFrame_RelStructure directionsInFrame = new DirectionsInFrame_RelStructure()
                .withDirection(direction);

        RoutePointsInFrame_RelStructure routePointsInFrame = new RoutePointsInFrame_RelStructure()
                .withRoutePoint(routePoints);

        RoutesInFrame_RelStructure routesInFrame = new RoutesInFrame_RelStructure();
        routesInFrame.getRoute_().addAll(routes);

        ScheduledStopPointsInFrame_RelStructure scheduledStopPointsInFrame = new ScheduledStopPointsInFrame_RelStructure()
                .withScheduledStopPoint(scheduledStopPoints);

        ServiceFrame serviceFrame = new ServiceFrame()
                .withVersion("any")
                .withId("AVI:ServiceFrame:WF149") // @todo: make dynamic
                .withNetwork(network)
                .withDirections(directionsInFrame)
                .withRoutePoints(routePointsInFrame)
                .withRoutes(routesInFrame)
                //.withLines(createLines()) // @todo: change null argument to real collection
                //.withDestinationDisplays()
                .withScheduledStopPoints(scheduledStopPointsInFrame);
                //.withServicePatterns(createServicePatterns(null)) // @todo: change null argument to real collection
                //.withStopAssignments(createStopAssignments(null)); // @todo: change null argument to real collection
        return getObjectFactory().createServiceFrame(serviceFrame);
    }

    public JAXBElement<ServiceCalendarFrame> createServiceCalendarFrame() {
        ServiceCalendarFrame serviceCalendarFrame = new ServiceCalendarFrame();
        return getObjectFactory().createServiceCalendarFrame(serviceCalendarFrame);
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
                .withId("AVI:TimetableFrame:TF01") // @todo: make dynamic
                .withValidityConditions(validityConditionsRelStructure)
                .withName(createMultilingualString("Rute for 3 dager frem i tid"))
                .withVehicleModes(VehicleModeEnumeration.AIR)
                .withVehicleJourneys(journeysInFrameRelStructure);
        return getObjectFactory().createTimetableFrame(timetableFrame);
    }

    /**
     * @todo: Rework and make more generic..
     */
    public JAXBElement<Authority> createAuthority() {
        Authority authority = new Authority()
                .withVersion("1")
                .withId(String.format("%s:Company:1", AVINOR_ID));

        JAXBElement<String> companyNumber = getObjectFactory().createOrganisation_VersionStructureCompanyNumber("985198292");
        JAXBElement<MultilingualString> name = getObjectFactory().createOrganisation_VersionStructureName(createMultilingualString("Avinor"));
        JAXBElement<MultilingualString> legalName = getObjectFactory().createOrganisation_VersionStructureLegalName(createMultilingualString("AVINOR AS"));
        JAXBElement<List<OrganisationTypeEnumeration>> organisationTypes = getObjectFactory().createOrganisation_VersionStructureOrganisationType(Arrays.asList(OrganisationTypeEnumeration.AUTHORITY));

        ContactStructure contactStructure = new ContactStructure()
                .withPhone("0047 815 30 550")
                .withUrl("http://avinor.no/")
                .withFurtherDetails(createMultilingualString("Kontaktskjema på websider"));

        JAXBElement<ContactStructure> contactDetails = getObjectFactory().createOrganisation_VersionStructureContactDetails(contactStructure);
        List<JAXBElement<?>> jaxbElements = Arrays.asList(companyNumber, name, legalName, organisationTypes, contactDetails);
        authority.withRest(jaxbElements);
        return getObjectFactory().createAuthority(authority);
    }

    /**
     * @todo: Rework and make more generic..
     */
    public JAXBElement<Operator> createOperator(String airlineIATA) {
        Operator operator = new Operator()
                .withVersion("1")
                .withId(String.format("%s:Company:2", airlineIATA));

        JAXBElement<String> companyNumber = getObjectFactory().createOrganisation_VersionStructureCompanyNumber("985615616");
        JAXBElement<MultilingualString> name = getObjectFactory().createOrganisation_VersionStructureName(createMultilingualString("Unibuss"));
        JAXBElement<MultilingualString> legalName = getObjectFactory().createOrganisation_VersionStructureLegalName(createMultilingualString("UNIBUSS AS"));
        JAXBElement<List<OrganisationTypeEnumeration>> organisationTypes = getObjectFactory().createOrganisation_VersionStructureOrganisationType(Arrays.asList(OrganisationTypeEnumeration.OPERATOR));

        ContactStructure contactStructure = new ContactStructure()
                .withPhone("0047 177")
                .withUrl("http://www.ruter.no")
                .withFurtherDetails(createMultilingualString("Kontaktskjema på websider"));

        JAXBElement<ContactStructure> contactDetails = getObjectFactory().createOrganisation_VersionStructureContactDetails(contactStructure);
        List<JAXBElement<?>> jaxbElements = Arrays.asList(companyNumber, name, legalName, organisationTypes, contactDetails);
        operator.withRest(jaxbElements);
        return getObjectFactory().createOperator(operator);
    }

    /**
     * @todo: Make this more generic
     */
    public AvailabilityCondition createAvailabilityCondition() {
        AvailabilityCondition availabilityCondition = new AvailabilityCondition()
                .withVersion("any")
                .withId("AVI:AvailabilityCondition:1")
                .withDescription(createMultilingualString("Flyruter fra idag og tre dager fremover"))
                .withFromDate(ZonedDateTime.now())
                .withToDate(ZonedDateTime.now().plusDays(3L));
        return availabilityCondition;
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
            JAXBElement<ScheduledStopPointRefStructure> scheduledStopPointRef = getObjectFactory().createScheduledStopPointRef(scheduledStopPointReference);
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
                    .withId(String.format("%s:PointProjection:%s101A0A0061101001", AVINOR_ID, flightId)) // @todo: generate postfix id in a serie
                    .withProjectedPointRef(pointRefStructure);
            Projections_RelStructure projections = new Projections_RelStructure()
                    .withProjectionRefOrProjection(getObjectFactory().createPointProjection(pointProjection));
            RoutePoint routePoint = new RoutePoint()
                    .withVersion("1")
                    .withId(String.format("%s:RoutePoint:%s101A0A0061101001", AVINOR_ID, flightId)) // @todo: generate postfix id in a serie
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
                    .withProjectionRefOrProjection(getObjectFactory().createPointProjection(pointProjection));
            RoutePoint routePoint = new RoutePoint()
                    .withVersion("1")
                    .withId("AVI:RoutePoint:0061101A0A0061101001") // @todo: make dynamic
                    .withProjections(projections);
            routePoints.add(routePoint);
        });
        return routePoints;
    }

    public List<JAXBElement<Route>> createRoutes(List<RoutePoint> routePoints, String flightId, String routePath, Direction direction) {
        PointsOnRoute_RelStructure pointsOnRoute = new PointsOnRoute_RelStructure();
        routePoints.forEach(routePoint -> {
            RoutePointRefStructure routePointReference = new RoutePointRefStructure()
                    .withVersion("1")
                    .withRef(routePoint.getId());
            PointOnRoute pointOnRoute = new PointOnRoute()
                    .withVersion("any")
                    .withId(String.format("%s:PointOnRoute:%s101001-0", AVINOR_ID, flightId)) // @todo: fix generation of serial numbers
                    //.withOrder(BigInteger.valueOf(stopover.getOrder())); // @todo: fix support for order values or implement counter
                    .withPointRef(getObjectFactory().createRoutePointRef(routePointReference));
            pointsOnRoute.getPointOnRoute().add(pointOnRoute);
        });
        DirectionRefStructure directionRefStructure = new DirectionRefStructure()
                .withRef(direction.getId());
        Route route = new Route()
                .withVersion("1")
                .withId(String.format("%s:Route:%s101", AVINOR_ID, flightId))
                .withName(createMultilingualString(String.format("%s: %s", flightId, routePath)))
                .withPointsInSequence(pointsOnRoute)
                .withDirectionRef(directionRefStructure);
        return Collections.singletonList(getObjectFactory().createRoute(route));
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
                    .withPointRef(getObjectFactory().createRoutePointRef(routePointReference));
            pointsOnRoute.getPointOnRoute().add(pointOnRoute);
        });
        Route route = new Route()
                .withVersion("1")
                .withId("AVI:Route:0061101")
                .withName(createMultilingualString("WF149"))
                .withPointsInSequence(pointsOnRoute); // @todo: make dynamic
                //.withDirectionRef();
        routesInFrame.getRoute_().add(getObjectFactory().createRoute(route));
        return routesInFrame;
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
        linesInFrame.getLine_().add(getObjectFactory().createLine(line));
        return null;
    }

    private List<ScheduledStopPoint> createScheduledStopPoints(ScheduledDirectFlight directFlight) {
        ScheduledStopPoint scheduledDepartureStopPoint = new ScheduledStopPoint()
                .withVersion("1")
                .withId(String.format("%s:StopPoint:%s101001", AVINOR_ID, directFlight.getAirlineFlightId()))
                .withName(createMultilingualString(directFlight.getDepartureAirportIATA())); // @todo: change to airport name when available
        ScheduledStopPoint scheduledArrivalStopPoint = new ScheduledStopPoint()
                .withVersion("1")
                .withId(String.format("%s:StopPoint:%s101002", AVINOR_ID, directFlight.getAirlineFlightId()))
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

    public ServicePatternsInFrame_RelStructure createServicePatterns(List<ScheduledStopPoint> scheduledStopPoints) {
        StopPointsInJourneyPattern_RelStructure stopPointsInJourneyPattern = new StopPointsInJourneyPattern_RelStructure();
        scheduledStopPoints.forEach(stopPoint -> {
            StopPointInJourneyPattern stopPointInJourneyPattern = new StopPointInJourneyPattern()
                    .withVersion("1")
                    .withId("AVI:StopPointInJourneyPattern:0061101001")
                    .withOrder(BigInteger.ONE) // @todo: fix a counter
                    .withScheduledStopPointRef(getObjectFactory().createScheduledStopPointRef(new ScheduledStopPointRefStructure().withVersion("1").withRef("AVI:StopPoint:0061101001")));
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
            stopAssignmentsInFrame.getStopAssignment().add(getObjectFactory().createStopAssignment(passengerStopAssignment));
        });
        return stopAssignmentsInFrame;
    }

    public Direction createDirection(String flightId) {
        return new Direction()
                .withId(String.format("%s:Route:%s101:Direction", AVINOR_ID, flightId))
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

    public Codespace getAvinorCodespace() {
        return avinorCodespace;
    }

    public Codespace getNhrCodespace() {
        return nhrCodespace;
    }

    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }
}
