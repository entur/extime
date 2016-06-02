package no.rutebanken.extime.routes.avinor;

import no.rutebanken.extime.model.ScheduledDirectFlight;
import no.rutebanken.extime.model.ScheduledStopover;
import no.rutebanken.extime.model.ScheduledStopoverFlight;
import no.rutebanken.netex.model.*;
import no.rutebanken.netex.model.PublicationDeliveryStructure.DataObjects;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @todo: inject the netex object factory as a spring bean instead of doing: new ObjectFactory() each call
 */
@Component(value = "scheduledFlightToNetexConverter")
public class ScheduledFlightToNetexConverter {

    public PublicationDeliveryStructure convertToNetex(ScheduledDirectFlight directFlight) {
        String routePath = String.format("%s-%s", directFlight.getDepartureAirportIATA(), directFlight.getArrivalAirportIATA());
        return createPublicationDeliveryStructure(directFlight.getAirlineFlightId(), routePath);
    }

    public PublicationDeliveryStructure convertToNetex(ScheduledStopoverFlight stopoverFlight) {
        return null;
    }

    public PublicationDeliveryStructure createPublicationDeliveryStructure(String flightId, String routePath) {
        DataObjects dataObjects = new DataObjects();
        dataObjects.getCompositeFrameOrCommonFrame().add(createCompositeFrame());

        return new PublicationDeliveryStructure()
                .withVersion("1.0")
                .withPublicationTimestamp(ZonedDateTime.now())
                .withParticipantRef("AVI")
                //.withPublicationRequest(publicationRequestStructure)
                //.withPublicationRefreshInterval(createDuration("P24H"))
                .withPublicationRefreshInterval(createDuration(""))
                .withDescription(createMultilingualString(String.format("Rute flight %s: %s", flightId, routePath)))
                .withDataObjects(dataObjects);
    }

    public JAXBElement<CompositeFrame> createCompositeFrame() {
        CompositeFrame compositeFrame = new CompositeFrame()
                .withVersion("1")
                .withCreated(ZonedDateTime.now())
                .withId("AVI:Norway:CompositeFrame:WF149") // @todo: make dynamic
                .withCodespaces(createCodespaces())
                .withFrames(createFrames());
        return new ObjectFactory().createCompositeFrame(compositeFrame);
    }

    /**
     * @todo: make these codespaces accessible at class level, for references
     */
    public Codespaces_RelStructure createCodespaces() {
        Codespace avinorCodespace = new Codespace()
                .withId("avinor")
                .withXmlns("AVI")
                .withXmlnsUrl("https://avinor.no/");
        Codespace nhrCodespace = new Codespace()
                .withId("nhr")
                .withXmlns("NHR")
                .withXmlnsUrl("http://www.rutebanken.no/nasjonaltholdeplassregister");
        return new Codespaces_RelStructure()
                .withCodespaceRefOrCodespace(Arrays.asList(avinorCodespace, nhrCodespace));
    }

    public Frames_RelStructure createFrames() {
        Frames_RelStructure framesRelStructure = new Frames_RelStructure();
        framesRelStructure.getCommonFrame().add(createResourceFrame());
        //framesRelStructure.getCommonFrame().add(createSiteFrame());
        //framesRelStructure.getCommonFrame().add(createSiteFrame());
        //framesRelStructure.getCommonFrame().add(createServiceCalendarFrame());
        //framesRelStructure.getCommonFrame().add(createTimetableFrame());
        return framesRelStructure;
    }

    public JAXBElement<ResourceFrame> createResourceFrame() {
        CodespaceRefStructure codespaceRefStructure = new CodespaceRefStructure()
                .withRef("avinor"); // @todo: make dynamic, maps to Codespace with id=avinor

        LocaleStructure localeStructure = new LocaleStructure()
                .withTimeZone("CET")
                .withSummerTimeZone("CEST")
                .withDefaultLanguage("no");

        VersionFrameDefaultsStructure versionFrameDefaultsStructure = new VersionFrameDefaultsStructure()
                .withDefaultCodespaceRef(codespaceRefStructure)
                .withDefaultLocale(localeStructure);
                //.withDefaultLocationSystem("EPSG:4326");

        OrganisationsInFrame_RelStructure organisationsInFrame = new OrganisationsInFrame_RelStructure();
        organisationsInFrame.getOrganisation_().add(createAuthority());
        organisationsInFrame.getOrganisation_().add(createOperator());

        ResourceFrame resourceFrame = new ResourceFrame()
                .withVersion("any")
                .withId("AVI:ResourceFrame:RF1")
                .withFrameDefaults(versionFrameDefaultsStructure) // @todo: make dynamic
                .withOrganisations(organisationsInFrame);
        return new ObjectFactory().createResourceFrame(resourceFrame);
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
        return new ObjectFactory().createSiteFrame(siteFrame);
    }

    public JAXBElement<ServiceFrame> createServiceFrame() {
        Network network = new Network()
                .withVersion("1")
                .withChanged(ZonedDateTime.now())
                .withId("AVI:GroupOfLine:AvinorFly")
                .withName(createMultilingualString("Avinor"));

        Direction direction = new Direction()
                .withName(createMultilingualString(""))
                .withDirectionType(DirectionTypeEnumeration.OUTBOUND);
        DirectionsInFrame_RelStructure directionsInFrame = new DirectionsInFrame_RelStructure()
                .withDirection(direction);

        RoutePointsInFrame_RelStructure routePointsInFrame = new RoutePointsInFrame_RelStructure()
                .withRoutePoint(createRoutePoints(null)); //@todo: change null argument to a real collection

        ServiceFrame serviceFrame = new ServiceFrame()
                .withVersion("any")
                .withId("AVI:ServiceFrame:WF149") // @todo: make dynamic
                .withNetwork(network)
                .withDirections(directionsInFrame)
                .withRoutePoints(routePointsInFrame)
                .withRoutes(createRoutes(null))
                .withLines(createLines()) // @todo: change null argument to real collection
                //.withDestinationDisplays()
                .withScheduledStopPoints(createScheduledStopPoints(null)) // @todo: change null argument to real collection
                .withServicePatterns(createServicePatterns(null)) // @todo: change null argument to real collection
                .withStopAssignments(createStopAssignments(null)); // @todo: change null argument to real collection
        return new ObjectFactory().createServiceFrame(serviceFrame);
    }

    public JAXBElement<ServiceCalendarFrame> createServiceCalendarFrame() {
        ServiceCalendarFrame serviceCalendarFrame = new ServiceCalendarFrame();
        return new ObjectFactory().createServiceCalendarFrame(serviceCalendarFrame);
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
        return new ObjectFactory().createTimetableFrame(timetableFrame);
    }

    /**
     * @todo: Rework and make more generic..
     */
    public JAXBElement<Authority> createAuthority() {
        Authority authority = new Authority()
                .withVersion("1")
                .withId("RUT:Company:1");

        JAXBElement<String> companyNumber = new ObjectFactory().createOrganisation_VersionStructureCompanyNumber("991609407");
        JAXBElement<MultilingualString> name = new ObjectFactory().createOrganisation_VersionStructureName(createMultilingualString("Avinor"));
        JAXBElement<MultilingualString> legalName = new ObjectFactory().createOrganisation_VersionStructureLegalName(createMultilingualString("AVINOR AS"));
        JAXBElement<List<OrganisationTypeEnumeration>> organisationTypes = new ObjectFactory().createOrganisation_VersionStructureOrganisationType(Arrays.asList(OrganisationTypeEnumeration.AUTHORITY));

        ContactStructure contactStructure = new ContactStructure()
                .withPhone("0047 815 30 550")
                .withUrl("http://avinor.no/")
                .withFurtherDetails(createMultilingualString("Kontaktskjema på websider"));

        JAXBElement<ContactStructure> contactDetails = new ObjectFactory().createOrganisation_VersionStructureContactDetails(contactStructure);
        List<JAXBElement<?>> jaxbElements = Arrays.asList(companyNumber, name, legalName, organisationTypes, contactDetails);
        authority.withRest(jaxbElements);
        return new ObjectFactory().createAuthority(authority);
    }

    /**
     * @todo: Rework and make more generic..
     */
    public JAXBElement<Operator> createOperator() {
        Operator operator = new Operator()
                .withVersion("1")
                .withId("RUT:Company:1");

        JAXBElement<String> companyNumber = new ObjectFactory().createOrganisation_VersionStructureCompanyNumber("985615616");
        JAXBElement<MultilingualString> name = new ObjectFactory().createOrganisation_VersionStructureName(createMultilingualString("Unibuss"));
        JAXBElement<MultilingualString> legalName = new ObjectFactory().createOrganisation_VersionStructureLegalName(createMultilingualString("UNIBUSS AS"));
        JAXBElement<List<OrganisationTypeEnumeration>> organisationTypes = new ObjectFactory().createOrganisation_VersionStructureOrganisationType(Arrays.asList(OrganisationTypeEnumeration.OPERATOR));

        ContactStructure contactStructure = new ContactStructure()
                .withPhone("0047 177")
                .withUrl("http://www.ruter.no")
                .withFurtherDetails(createMultilingualString("Kontaktskjema på websider"));

        JAXBElement<ContactStructure> contactDetails = new ObjectFactory().createOrganisation_VersionStructureContactDetails(contactStructure);
        List<JAXBElement<?>> jaxbElements = Arrays.asList(companyNumber, name, legalName, organisationTypes, contactDetails);
        operator.withRest(jaxbElements);
        return new ObjectFactory().createOperator(operator);
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
            JAXBElement<ScheduledStopPointRefStructure> scheduledStopPointRef = new ObjectFactory().createScheduledStopPointRef(scheduledStopPointReference);
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
                    .withProjectionRefOrProjection(new ObjectFactory().createPointProjection(pointProjection));
            RoutePoint routePoint = new RoutePoint()
                    .withVersion("1")
                    .withId("AVI:RoutePoint:0061101A0A0061101001") // @todo: make dynamic
                    .withProjections(projections);
            routePoints.add(routePoint);
        });
        return routePoints;
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
                    .withPointRef(new ObjectFactory().createRoutePointRef(routePointReference));
            pointsOnRoute.getPointOnRoute().add(pointOnRoute);
        });
        Route route = new Route()
                .withVersion("1")
                .withId("AVI:Route:0061101")
                .withName(createMultilingualString("WF149"))
                .withPointsInSequence(pointsOnRoute); // @todo: make dynamic
                //.withDirectionRef();
        routesInFrame.getRoute_().add(new ObjectFactory().createRoute(route));
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
        linesInFrame.getLine_().add(new ObjectFactory().createLine(line));
        return null;
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
                    .withScheduledStopPointRef(new ObjectFactory().createScheduledStopPointRef(new ScheduledStopPointRefStructure().withVersion("1").withRef("AVI:StopPoint:0061101001")));
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
            stopAssignmentsInFrame.getStopAssignment().add(new ObjectFactory().createStopAssignment(passengerStopAssignment));
        });
        return stopAssignmentsInFrame;
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

}
