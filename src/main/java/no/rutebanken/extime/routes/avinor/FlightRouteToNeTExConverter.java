package no.rutebanken.extime.routes.avinor;

import no.rutebanken.extime.model.FlightRouteDataSet;
import no.rutebanken.netex.model.*;
import org.apache.camel.Body;
import org.apache.camel.Header;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
        List<FlightRouteDataSet> sasFlightList = flightRouteList.stream()
                .filter(flightRouteDataSet -> flightRouteDataSet.getFlightId().equalsIgnoreCase("SK260"))
                .collect(Collectors.toList());

        System.out.println("Complete SK260 flights list:");
        sasFlightList.stream().forEach(System.out::println);

        return Arrays.asList(convertToNetex(sasFlightList));
    }

    private PublicationDeliveryStructure convertToNetex(List<FlightRouteDataSet> sasFlightList) {
        ObjectFactory netexObjectFactory = new ObjectFactory();
        String flightId = sasFlightList.get(0).getFlightId().trim();
        String departureAirportName = sasFlightList.get(0).getDepartureAirportName().getName().trim();
        String arrivalAirportName = sasFlightList.get(0).getArrivalAirportName().getName().trim();
        String routeString = generateRouteString(departureAirportName, arrivalAirportName);
        String routeName = generateRouteName(departureAirportName, arrivalAirportName);

        // @todo: Add SiteFrame with stopPlaces/StopPlace

        // @todo: Add ServiceFrame with routes, lines, and scheduledStopPoints

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

        // iterate over all flightroutes and create a corresponding service journey
        List<ServiceJourney> serviceJourneyList = new ArrayList<>();
        sasFlightList.forEach(flightRouteDataSet -> {
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
        framesRelStructure.getCommonFrame().add(timetableFrameElement);

        CompositeFrame compositeFrame = netexObjectFactory.createCompositeFrame()
                .withVersion("1")
                .withId(String.format("%s:cf:cf01", flightId.toLowerCase()))
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
                .withDescription(createMultilingualString(String.format("Request object for %s timetable", flightId)))
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
