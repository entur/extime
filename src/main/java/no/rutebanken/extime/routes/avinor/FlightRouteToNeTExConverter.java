package no.rutebanken.extime.routes.avinor;

import no.avinor.flydata.xjc.model.airport.AirportName;
import no.rutebanken.extime.model.FlightRouteDataSet;
import no.rutebanken.netex.model.*;
import org.apache.camel.Body;
import org.apache.camel.Header;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.math.BigInteger;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_AIRLINE_IATA_MAP;

@Component(value = "flightRouteToNetexConverter")
public class FlightRouteToNeTExConverter {

    public List<PublicationDeliveryStructure> convertRoutesToNetexFormat(
            @Header(HEADER_AIRLINE_IATA_MAP) Map<String, String> airlineIATAMap,
            @Body List<FlightRouteDataSet> flightRouteList) {
        List<PublicationDeliveryStructure> netexStructures = new ArrayList<>();
        Map<String, List<FlightRouteDataSet>> flightRouteMap =
                flightRouteList.stream()
                        .collect(Collectors.groupingBy(FlightRouteDataSet::getFlightId));
        flightRouteMap.forEach(
                (flightId, flightRoutesByFlightId) -> {
                    String airlineName = airlineIATAMap.get(flightRouteList.get(0).getAirlineIATA());
                    PublicationDeliveryStructure publicationDeliveryStructure = convertToNetex(airlineName, flightId, flightRoutesByFlightId);
                    if (publicationDeliveryStructure != null) {
                        netexStructures.add(publicationDeliveryStructure);
                    }
                }
        );
        return netexStructures;
    }

    private PublicationDeliveryStructure convertToNetex(String airlineName, String flightId, List<FlightRouteDataSet> flightRouteList) {
        ObjectFactory netexObjectFactory = new ObjectFactory();
        AirportName departureAirportName = flightRouteList.get(0).getDepartureAirportName();
        AirportName arrivalAirportName = flightRouteList.get(0).getArrivalAirportName();
        String routeString = generateRouteString(departureAirportName.getName(), arrivalAirportName.getName());
        String routeName = generateRouteName(departureAirportName.getName(), arrivalAirportName.getName());

        Route route = netexObjectFactory.createRoute()
                .withVersion("any")
                .withId(String.format("%s:rt:%s", flightId.toLowerCase(), routeString))
                .withName(createMultilingualString(routeName));

        Line line = netexObjectFactory.createLine()
                .withVersion("any")
                .withId(String.format("%s:line", flightId.toLowerCase()))
                .withName(createMultilingualString(
                        String.format("Fly %s %s", departureAirportName, arrivalAirportName)))
                .withTransportMode(AllVehicleModesOfTransportEnumeration.AIR);
                //.withRoutes()

        AvailabilityCondition availabilityCondition = netexObjectFactory.createAvailabilityCondition()
                .withVersion("any")
                .withId(String.format("%s:ac:three_days_ahead", flightId.toLowerCase()))
                .withDescription(createMultilingualString("Flights three days ahead"))
                .withFromDate(ZonedDateTime.now())
                .withToDate(ZonedDateTime.now().plusDays(3L));

        ValidityConditions_RelStructure validityConditionsRelStructure = netexObjectFactory.createValidityConditions_RelStructure();
                //.withValidityConditionRefOrValidBetweenOrValidityCondition_(availabilityCondition);

        Codespace codespace = netexObjectFactory.createCodespace()
                .withId(airlineName.toLowerCase())
                .withXmlns(airlineName.toLowerCase())
                .withXmlnsUrl(String.format("http://%s.no", airlineName.toLowerCase()))
                .withDescription(String.format("Namespace for %s", airlineName));
        Codespaces_RelStructure codespacesRelStructure = netexObjectFactory.createCodespaces_RelStructure()
                .withCodespaceRefOrCodespace(codespace);
        CodespaceRefStructure codespaceRefStructure = netexObjectFactory.createCodespaceRefStructure()
                .withRef(airlineName.toLowerCase());
        VersionFrameDefaultsStructure versionFrameDefaultsStructure = netexObjectFactory.createVersionFrameDefaultsStructure()
                .withDefaultCodespaceRef(codespaceRefStructure);

        // iterate over all flightroutes and create a corresponding stop places, service journeys, etc...
        List<StopPlace> stopPlaceList = new ArrayList<>();
        List<ServiceJourney> serviceJourneyList = new ArrayList<>();
        flightRouteList.forEach(flightRouteDataSet -> {
            /* -==STOP-PLACES-PART==-*/

            StopPlace departureStopPlace = netexObjectFactory.createStopPlace()
                    .withVersion("any")
                    .withName(createMultilingualString(departureAirportName.getName()))
                    .withShortName(createMultilingualString(departureAirportName.getCode()))
                    //.withCentroid()
                    .withTransportMode(VehicleModeEnumeration.AIR)
                    .withStopPlaceType(StopTypeEnumeration.AIRPORT);
            StopPlace arrivalStopPlace = netexObjectFactory.createStopPlace()
                    .withVersion("any")
                    .withName(createMultilingualString(arrivalAirportName.getName()))
                    .withShortName(createMultilingualString(arrivalAirportName.getCode()))
                    //.withCentroid()
                    .withTransportMode(VehicleModeEnumeration.AIR)
                    .withStopPlaceType(StopTypeEnumeration.AIRPORT);
            stopPlaceList.add(departureStopPlace);
            stopPlaceList.add(arrivalStopPlace);

            /* -==SERVICE-JOURNEY-PART==-*/
/*
            Date departureTime = flightRouteDataSet.getDepartureFlight().getScheduleTime();
            String departureTimeFormat = DateFormatUtils.formatUTC(
                    departureTime, DateFormatUtils.ISO_TIME_TIME_ZONE_FORMAT.getPattern());
            Date arrivalTime = flightRouteDataSet.getArrivalFlight().getScheduleTime();
            String arrivalTimeFormat = DateFormatUtils.formatUTC(
                    arrivalTime, DateFormatUtils.ISO_TIME_TIME_ZONE_FORMAT.getPattern());
*/

            // @todo: calculate the actual runtime from =(arrivalTime-departureTime)
            VehicleJourneyRunTime vehicleJourneyRunTime = netexObjectFactory.createVehicleJourneyRunTime()
                    .withId(String.format("%s:vjrt:%s", flightId.toLowerCase(), routeString))
                    //.withTimingLinkRef()
                    .withRunTime(createDuration("PT50M"));

            VehicleJourneyRunTimes_RelStructure vehicleJourneyRunTimesRelStructure =
                    netexObjectFactory.createVehicleJourneyRunTimes_RelStructure()
                    .withVehicleJourneyRunTime(vehicleJourneyRunTime);

            DepartureStructure departureStructure = netexObjectFactory.createDepartureStructure()
                    //.withTime(LocalTime.parse(departureTimeFormat));
                    .withTime(LocalTime.now());
            Call departureCall = netexObjectFactory.createCall()
                    .withId(String.format("%s:call:%s:01", flightId.toLowerCase(), routeString))
                    .withOrder(BigInteger.ONE)
                    //.withScheduledStopPointRef()
                    .withDeparture(departureStructure)
                    .withNote(createMultilingualString("Departure"));

            ArrivalStructure arrivalStructure = netexObjectFactory.createArrivalStructure()
                    //.withTime(LocalTime.parse(arrivalTimeFormat));
                    .withTime(LocalTime.now());
            Call arrivalCall = netexObjectFactory.createCall()
                    .withId(String.format("%s:call:%s:02", flightId.toLowerCase(), routeString))
                    .withOrder(BigInteger.valueOf(2L))
                    //.withScheduledStopPointRef()
                    .withArrival(arrivalStructure)
                    .withNote(createMultilingualString("Arrival"));

            Calls_RelStructure callsRelStructure = netexObjectFactory.createCalls_RelStructure()
                    .withCallOrDatedCall(departureCall)
                    .withCallOrDatedCall(arrivalCall);

            ServiceJourney serviceJourney = netexObjectFactory.createServiceJourney()
                    .withVersion("any")
                    .withId(String.format("%s:sj:%s_0645", flightId.toLowerCase(), routeString))
                    //.withDepartureTime(LocalTime.parse(departureTimeFormat))
                    .withDepartureTime(LocalTime.now())
                    //.withLineRef();
                    .withRunTimes(vehicleJourneyRunTimesRelStructure)
                    .withCalls(callsRelStructure);
            serviceJourneyList.add(serviceJourney);
        });

        StopPlacesInFrame_RelStructure stopPlacesInFrameRelStructure = netexObjectFactory.createStopPlacesInFrame_RelStructure()
                .withStopPlace(stopPlaceList);

        SiteFrame siteFrame = netexObjectFactory.createSiteFrame()
                .withVersion("any")
                .withId(String.format("%s:sf:1", flightId.toLowerCase()))
                .withStopPlaces(stopPlacesInFrameRelStructure);
        JAXBElement<SiteFrame> siteFrameElement = netexObjectFactory.createSiteFrame(siteFrame);

        // @todo: Add ServiceFrame with routes, lines, and scheduledStopPoints here

        JourneysInFrame_RelStructure journeysInFrameRelStructure = netexObjectFactory.createJourneysInFrame_RelStructure();
        journeysInFrameRelStructure.getDatedServiceJourneyOrDeadRunOrServiceJourney().addAll(serviceJourneyList);

        TimetableFrame timetableFrame = netexObjectFactory.createTimetableFrame()
                .withVersion("1")
                .withId(String.format("%s:tf:v1", flightId.toLowerCase()))
                .withValidityConditions(validityConditionsRelStructure)
                .withName(createMultilingualString(
                        String.format("Tabell fra nå og tre dager frem i tid for %s", flightId)))
                //.withVehicleModes(VehicleModeEnumeration.AIR)
                .withVehicleJourneys(journeysInFrameRelStructure);
        JAXBElement<TimetableFrame> timetableFrameElement = netexObjectFactory.createTimetableFrame(timetableFrame);
        Frames_RelStructure framesRelStructure = netexObjectFactory.createFrames_RelStructure();
        framesRelStructure.getCommonFrame().add(siteFrameElement);
        framesRelStructure.getCommonFrame().add(timetableFrameElement);

        CompositeFrame compositeFrame = netexObjectFactory.createCompositeFrame()
                .withVersion("1")
                .withId(String.format("%s:cf:cf01", flightId.toLowerCase()))
                //.withValidityConditions()
                .withCodespaces(codespacesRelStructure)
                .withFrameDefaults(versionFrameDefaultsStructure)
                .withFrames(framesRelStructure);
        JAXBElement<CompositeFrame> compositeFrameElement = netexObjectFactory.createCompositeFrame(compositeFrame);

        PublicationDeliveryStructure.DataObjects dataObjects = netexObjectFactory.createPublicationDeliveryStructureDataObjects();
        dataObjects.getCompositeFrameOrCommonFrame().add(compositeFrameElement);

        AvailabilityCondition mainAvailabilityCondition = netexObjectFactory.createAvailabilityCondition()
                .withFromDate(ZonedDateTime.now())
                .withToDate(ZonedDateTime.now().plusDays(3L));
        JAXBElement<AvailabilityCondition> mainAvailabilityConditionElement =
                netexObjectFactory.createAvailabilityCondition(mainAvailabilityCondition);

        NetworkFrameTopicStructure.SelectionValidityConditions selectionValidityConditions =
                netexObjectFactory.createNetworkFrameTopicStructureSelectionValidityConditions();
        //selectionValidityConditions.getValidityCondition_().add(mainAvailabilityConditionElement);

        NetworkFrameTopicStructure networkFrameTopicStructure = netexObjectFactory.createNetworkFrameTopicStructure()
                .withSelectionValidityConditions(selectionValidityConditions);

        PublicationRequestStructure.Topics topics = netexObjectFactory.createPublicationRequestStructureTopics()
                .withNetworkFrameTopic(networkFrameTopicStructure);

        PublicationRequestStructure publicationRequestStructure = netexObjectFactory.createPublicationRequestStructure()
                .withVersion("1.0")
                .withRequestTimestamp(ZonedDateTime.now())
                .withParticipantRef("RUTEBANKEN")
                .withDescription(createMultilingualString(String.format("Request object of timetable for %s flight %s", airlineName, flightId)))
                .withTopics(topics);

        return netexObjectFactory.createPublicationDeliveryStructure()
                .withVersion("1.0")
                .withPublicationTimestamp(ZonedDateTime.now())
                .withParticipantRef("Avinor")
                .withPublicationRequest(publicationRequestStructure)
                //.withPublicationRefreshInterval(createDuration("P24H"))
                .withPublicationRefreshInterval(createDuration(""))
                .withDescription(createMultilingualString("Simple timetable section for air traffic"))
                .withDataObjects(dataObjects);
    }

    private String generateRouteName(String departureAirportName, String arrivalAirportName) {
        return String.format("Fly fra %s til %s", departureAirportName, arrivalAirportName);
    }

    private String generateRouteString(String departureAirportName, String arrivalAirportName) {
        return String.format("%s_to_%s", departureAirportName.toLowerCase(), arrivalAirportName.toLowerCase());
    }

    public MultilingualString createMultilingualString(String value) {
        return new ObjectFactory().createMultilingualString()
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
