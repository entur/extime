package no.rutebanken.extime.routes.avinor;

import no.rutebanken.extime.model.ScheduledDirectFlight;
import no.rutebanken.extime.model.ScheduledStopover;
import no.rutebanken.extime.model.ScheduledStopoverFlight;
import no.rutebanken.netex.model.*;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static no.rutebanken.netex.model.PublicationDeliveryStructure.DataObjects;

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
                .withDescription(createMultilingualString(String.format("Rute flight %s: %s", flightId, routePath)));
                //.withDataObjects(dataObjects);
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
                .withXmlnsUrl("http://www.rutebanken.no/nasjonltholdeplassregister");
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
        // @todo: create Authority and Operator here...

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
        ServiceFrame serviceFrame = new ServiceFrame()
                .withVersion("any")
                .withId("AVI:ServiceFrame:WF149") // @todo: make dynamic
                ;
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

    public MultilingualString createMultilingualString(String value) {
        return new MultilingualString()
                .withLang("no")
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
