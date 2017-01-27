package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.config.CamelRouteDisabler;
import no.rutebanken.extime.fixtures.LineDataSetFixture;
import no.rutebanken.extime.model.*;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rutebanken.netex.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.xml.bind.JAXBElement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.extime.Constants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@SuppressWarnings("unchecked")
@ActiveProfiles("test")
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {CamelRouteDisabler.class, LineDataToNetexConverter.class})
public class LineDataToNetexConverterTest {

    @Autowired
    private LineDataToNetexConverter netexConverter;

    @Test
    public void testLineWithDirectRoutes() throws Exception {
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet(
                "DY", "OSL-BGO", Arrays.asList(Pair.of("OSL-BGO", 1), Pair.of("BGO-OSL", 1)));

        JAXBElement<PublicationDeliveryStructure> publicationDeliveryElement = netexConverter.convertToNetex(lineDataSet);
        PublicationDeliveryStructure publicationDelivery = publicationDeliveryElement.getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = getFrames(ServiceFrame.class, dataObjectFrames).get(0);
        TimetableFrame timetableFrame = getFrames(TimetableFrame.class, dataObjectFrames).get(0);
        ServiceCalendarFrame serviceCalendarFrame = getFrames(ServiceCalendarFrame.class, dataObjectFrames).get(0);

        Line line = (Line) serviceFrame.getLines().getLine_().get(0).getValue();

        assertValidNetwork(serviceFrame.getNetwork(), lineDataSet.getAirlineIata());
        assertValidLine(line, lineDataSet);
        assertValidRoutes(serviceFrame.getRoutes(), lineDataSet, line);

/*
        List<Object> validityConditions = compositeFrame.getValidityConditions().getValidityConditionRefOrValidBetweenOrValidityCondition_();
        AvailabilityCondition availabilityCondition = (AvailabilityCondition) ((JAXBElement) validityConditions.get(0)).getValue();

        Assertions.assertThat(availabilityCondition.getFromDate())
                .isEqualTo(requestPeriodFromDateTime);
        Assertions.assertThat(availabilityCondition.getToDate())
                .isEqualTo(requestPeriodToDateTime);
*/

        // check journey patterns
        List<JAXBElement<?>> journeyPatternElements = serviceFrame.getJourneyPatterns().getJourneyPattern_OrJourneyPatternView();

        List<JourneyPattern> journeyPatterns = journeyPatternElements.stream()
                .map(JAXBElement::getValue)
                .map(journeyPattern -> (JourneyPattern) journeyPattern)
                .collect(Collectors.toList());

        assertThat(journeyPatterns)
                .hasSize(2)
                .extracting("id", "routeRef.ref")
                .contains(tuple("AVI:JourneyPattern:DY_OSL-BGO", "AVI:Route:DY_OSL-BGO"), tuple("AVI:JourneyPattern:DY_BGO-OSL", "AVI:Route:DY_BGO-OSL"));

        journeyPatterns.forEach(journeyPattern -> assertThat(journeyPattern.getPointsInSequence()
                .getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()).hasSize(2));

        // check destination displays
        List<DestinationDisplay> destinationDisplay = serviceFrame.getDestinationDisplays().getDestinationDisplay();
        assertThat(destinationDisplay)
                .hasSize(4)
                .extracting("id")
                .contains("AVI:DestinationDisplay:DY_OSL-BGO", "AVI:DestinationDisplay:DY_BGO-OSL", "AVI:DestinationDisplay:DYOSLBGO-OSL", "AVI:DestinationDisplay:DYOSLBGO-BGO");
        assertThat(destinationDisplay.get(0).getVias())
                .isNull();
        assertThat(destinationDisplay.get(1).getVias())
                .isNull();

        List<ServiceJourney> serviceJourneys = timetableFrame.getVehicleJourneys().getDatedServiceJourneyOrDeadRunOrServiceJourney().stream()
                .map(journey -> (ServiceJourney) journey)
                .collect(Collectors.toList());

        // check first service journey
        //Assertions.assertThat(serviceJourneys.get(0).getDepartureTime()).isEqualTo(flight1DepartureTime);
        //Assertions.assertThat(serviceJourneys.get(0).getJourneyPatternRef().getValue().getRef()).isEqualTo("AVI:JourneyPattern:OSL-BGO"); // TODO fix journey pattern id
        //Assertions.assertThat(serviceJourneys.get(0).getPublicCode()).isEqualTo("DY602"); // TODO we will probably know the flight ids
        assertThat(serviceJourneys.get(0).getLineRef().getValue().getRef()).isEqualTo("AVI:Line:DY-OSL-BGO");
        //Assertions.assertThat(serviceJourneys.get(0).getDayTypes().getDayTypeRef().get(0).getValue().getRef()).isEqualTo("AVI:DayType:DYOSLBGO-Mon_30"); // TODO consider if it is better to know the operating days before test

        List<TimetabledPassingTime> departurePassingTimes = serviceJourneys.get(0).getPassingTimes().getTimetabledPassingTime();
        assertThat(departurePassingTimes).hasSize(2);
        //Assertions.assertThat(departurePassingTimes.get(0).getPointInJourneyPatternRef().getValue().getRef()).isEqualTo(""); // TODO fix journey pattern id
        //Assertions.assertThat(departurePassingTimes.get(0).getDepartureTime()).isEqualTo(flight1DepartureTime);
        //Assertions.assertThat(departurePassingTimes.get(1).getPointInJourneyPatternRef().getValue().getRef()).isEqualTo(""); // TODO fix journey pattern id
        //Assertions.assertThat(departurePassingTimes.get(1).getArrivalTime()).isEqualTo(flight1ArrivalTime);

        // check second service journey
        //Assertions.assertThat(serviceJourneys.get(1).getDepartureTime()).isEqualTo(flight2DepartureTime);
        //Assertions.assertThat(serviceJourneys.get(1).getJourneyPatternRef().getValue().getRef()).isEqualTo("AVI:JourneyPattern:OSL-BGO"); // TODO fix journey pattern id
        //Assertions.assertThat(serviceJourneys.get(1).getPublicCode()).isEqualTo("DY633");  // TODO we will probably know the flight ids
        assertThat(serviceJourneys.get(1).getLineRef().getValue().getRef()).isEqualTo("AVI:Line:DY-OSL-BGO");
        //Assertions.assertThat(serviceJourneys.get(1).getDayTypes().getDayTypeRef().get(0).getValue().getRef()).isEqualTo("AVI:DayType:DYOSLBGO-Tue_31"); // TODO consider if it is better to know the operating days before test

        List<TimetabledPassingTime> arrivalPassingTimes = serviceJourneys.get(1).getPassingTimes().getTimetabledPassingTime();
        assertThat(arrivalPassingTimes).hasSize(2);
        //Assertions.assertThat(departurePassingTimes.get(0).getPointInJourneyPatternRef().getValue().getRef()).isEqualTo(""); // TODO fix journey pattern id
        //Assertions.assertThat(arrivalPassingTimes.get(0).getDepartureTime()).isEqualTo(flight2DepartureTime);
        //Assertions.assertThat(departurePassingTimes.get(1).getPointInJourneyPatternRef().getValue().getRef()).isEqualTo(""); // TODO fix journey pattern id
        //Assertions.assertThat(arrivalPassingTimes.get(1).getArrivalTime()).isEqualTo(flight2ArrivalTime);

        // check day types
        List<DayType> dayTypes = serviceCalendarFrame.getDayTypes().getDayType_().stream()
                .map(JAXBElement::getValue)
                .map(dayType -> (DayType) dayType)
                .collect(Collectors.toList());

        assertThat(dayTypes)
                .hasSize(2);
                //.extracting("id")
                //.contains("AVI:DayType:DYOSLBGO-Mon_30", "AVI:DayType:DYOSLBGO-Tue_31"); // TODO consider if it is better to know the operating days before test

        // check day type assignments
/*
        Assertions.assertThat(serviceCalendarFrame.getDayTypeAssignments().getDayTypeAssignment())
                .hasSize(2)
                .extracting("date", "dayTypeRef.value.ref")
                .contains(
                        tuple(flight1DateOfOperation, "AVI:DayType:DYOSLBGO-Mon_30"),
                        tuple(flight2DateOfOperation, "AVI:DayType:DYOSLBGO-Tue_31")
                );
*/
    }

    @Test
    public void testLineWithRoundTripRoutes() throws Exception {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-BGO", 1), Pair.of("BGO-OSL", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("DY", "OSL-BGO", routeJourneyPairs);

        JAXBElement<PublicationDeliveryStructure> publicationDeliveryElement = netexConverter.convertToNetex(lineDataSet);
        PublicationDeliveryStructure publicationDelivery = publicationDeliveryElement.getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = getFrames(ServiceFrame.class, dataObjectFrames).get(0);

        Line line = (Line) serviceFrame.getLines().getLine_().get(0).getValue();

        assertValidNetwork(serviceFrame.getNetwork(), lineDataSet.getAirlineIata());
        assertValidLine(line, lineDataSet);
        assertValidRoutes(serviceFrame.getRoutes(), lineDataSet, line);
    }

    @Test
    public void testDestinationDisplaysNoVias() throws Exception {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-BGO", 1), Pair.of("BGO-OSL", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("DY", "OSL-BGO", routeJourneyPairs);

        JAXBElement<PublicationDeliveryStructure> publicationDeliveryElement = netexConverter.convertToNetex(lineDataSet);
        PublicationDeliveryStructure publicationDelivery = publicationDeliveryElement.getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = getFrames(ServiceFrame.class, dataObjectFrames).get(0);

        // check journey patterns for destination display reference
        List<JAXBElement<?>> journeyPatternElements = serviceFrame.getJourneyPatterns().getJourneyPattern_OrJourneyPatternView();

        List<JourneyPattern> journeyPatterns = journeyPatternElements.stream()
                .map(JAXBElement::getValue)
                .map(journeyPattern -> (JourneyPattern) journeyPattern)
                .collect(Collectors.toList());

        for (JourneyPattern journeyPattern : journeyPatterns) {
            if (journeyPattern.getId().equals("AVI:JourneyPattern:DY_OSL-BGO")) {
                DestinationDisplayRefStructure destinationDisplayRef = journeyPattern.getDestinationDisplayRef();
                assertThat(destinationDisplayRef).isNotNull();
                assertThat(destinationDisplayRef.getVersion()).isEqualTo(VERSION_ONE);
                assertThat(destinationDisplayRef.getRef()).isEqualTo("AVI:DestinationDisplay:DY_OSL-BGO");
            }
            if (journeyPattern.getId().equals("AVI:JourneyPattern:DY_BGO-OSL")) {
                DestinationDisplayRefStructure destinationDisplayRef = journeyPattern.getDestinationDisplayRef();
                assertThat(destinationDisplayRef).isNotNull();
                assertThat(destinationDisplayRef.getVersion()).isEqualTo(VERSION_ONE);
                assertThat(destinationDisplayRef.getRef()).isEqualTo("AVI:DestinationDisplay:DY_BGO-OSL");
            }
        }

        // check destination displays
        List<DestinationDisplay> destinationDisplays = serviceFrame.getDestinationDisplays().getDestinationDisplay();

        assertThat(destinationDisplays).extracting("version").contains(VERSION_ONE);

        assertThat(destinationDisplays)
                .hasSize(4)
                .extracting("id")
                .contains("AVI:DestinationDisplay:DY_OSL-BGO", "AVI:DestinationDisplay:DY_BGO-OSL",
                        "AVI:DestinationDisplay:DYOSLBGO-OSL", "AVI:DestinationDisplay:DYOSLBGO-BGO");

        for (DestinationDisplay destinationDisplay : destinationDisplays) {
            if (destinationDisplay.getId().equals("AVI:DestinationDisplay:DY_OSL-BGO")) {
                assertThat(destinationDisplay.getFrontText().getValue()).isEqualTo("Bergen");
            }
            if (destinationDisplay.getId().equals("AVI:DestinationDisplay:DY_BGO-OSL")) {
                assertThat(destinationDisplay.getFrontText().getValue()).isEqualTo("Oslo");
            }
        }
    }

    @Test
    public void testDestinationDisplaysWithVias() throws Exception {
        LineDataSet lineDataSet = LineDataSetFixture.createBasicLineDataSet("DY", "OSL-BGO");

        String firstDateOfOperation = "2017-01-30";

        LocalDate requestPeriodFromDate = LocalDate.parse(firstDateOfOperation);
        LocalDate requestPeriodToDate = requestPeriodFromDate.plusDays(1);

        OffsetTime offsetMidnight = OffsetTime.parse(OFFSET_MIDNIGHT_UTC).withOffsetSameLocal(ZoneOffset.UTC);
        OffsetDateTime requestPeriodFromDateTime = requestPeriodFromDate.atTime(offsetMidnight);
        OffsetDateTime requestPeriodToDateTime = requestPeriodToDate.atTime(offsetMidnight);

        AvailabilityPeriod availabilityPeriod = new AvailabilityPeriod(requestPeriodFromDateTime, requestPeriodToDateTime);
        lineDataSet.setAvailabilityPeriod(availabilityPeriod);

        FlightRoute mainRoute = new FlightRoute("OSL-SOG-BGO", "Oslo-Sogndal-Bergen");
        FlightRoute oppositeRoute = new FlightRoute("BGO-SOG-OSL", "Bergen-Sogndal-Oslo");
        List<FlightRoute> flightRoutes = Arrays.asList(mainRoute, oppositeRoute);
        lineDataSet.setFlightRoutes(flightRoutes);

        Map<String, Map<String, List<ScheduledFlight>>> routeJourneys = new HashMap<>();

        List<ScheduledStopover> flight1Stopovers = Lists.newArrayList(
                createScheduledStopover("OSL", null, OffsetTime.parse("07:10:00Z")),
                createScheduledStopover("SOG", OffsetTime.parse("07:50:00Z"), OffsetTime.parse("08:10:00Z")),
                createScheduledStopover("BGO", OffsetTime.parse("09:00:00Z"), null)
        );
        ScheduledFlight scheduledFlight1 = new ScheduledFlight();
        scheduledFlight1.setAirlineIATA("DY");
        scheduledFlight1.setAirlineName("Norwegian");
        scheduledFlight1.setAirlineFlightId("DY602");
        scheduledFlight1.setDateOfOperation(LocalDate.parse(firstDateOfOperation));
        scheduledFlight1.setScheduledStopovers(flight1Stopovers);
        List<ScheduledFlight> routeOslSogBgoFlights = Lists.newArrayList(scheduledFlight1);

        List<ScheduledStopover> flight2Stopovers = Lists.newArrayList(
                createScheduledStopover("BGO", null, OffsetTime.parse("07:10:00Z")),
                createScheduledStopover("SOG", OffsetTime.parse("07:50:00Z"), OffsetTime.parse("08:10:00Z")),
                createScheduledStopover("OSL", OffsetTime.parse("09:00:00Z"), null)
        );
        ScheduledFlight scheduledFlight2 = new ScheduledFlight();
        scheduledFlight2.setAirlineIATA("DY");
        scheduledFlight2.setAirlineName("Norwegian");
        scheduledFlight2.setAirlineFlightId("DY633");
        scheduledFlight2.setDateOfOperation(LocalDate.parse(firstDateOfOperation));
        scheduledFlight2.setScheduledStopovers(flight2Stopovers);
        List<ScheduledFlight> routeBgoSogOslFlights = Lists.newArrayList(scheduledFlight2);

        Map<String, List<ScheduledFlight>> dy602Flights = new HashMap<>();
        dy602Flights.put("DY602", routeOslSogBgoFlights);
        routeJourneys.put("OSL-SOG-BGO", dy602Flights);

        Map<String, List<ScheduledFlight>> dy633Flights = new HashMap<>();
        dy633Flights.put("DY633", routeBgoSogOslFlights);
        routeJourneys.put("BGO-SOG-OSL", dy633Flights);

        lineDataSet.setRouteJourneys(routeJourneys);

        JAXBElement<PublicationDeliveryStructure> publicationDeliveryElement = netexConverter.convertToNetex(lineDataSet);
        PublicationDeliveryStructure publicationDelivery = publicationDeliveryElement.getValue();

        // check the resulting publication delivery
        assertThat(publicationDelivery)
                .isNotNull();

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = publicationDelivery.getDataObjects().getCompositeFrameOrCommonFrame();
        CompositeFrame compositeFrame = getFrames(CompositeFrame.class, dataObjectFrames).get(0);
        List<JAXBElement<? extends Common_VersionFrameStructure>> frames = compositeFrame.getFrames().getCommonFrame();

        ServiceFrame serviceFrame = getFrames(ServiceFrame.class, frames).get(0);

        // check journey patterns for destination display reference
        List<JAXBElement<?>> journeyPatternElements = serviceFrame.getJourneyPatterns().getJourneyPattern_OrJourneyPatternView();

        List<JourneyPattern> journeyPatterns = journeyPatternElements.stream()
                .map(JAXBElement::getValue)
                .map(journeyPattern -> (JourneyPattern) journeyPattern)
                .collect(Collectors.toList());

        for (JourneyPattern journeyPattern : journeyPatterns) {
            if (journeyPattern.getId().equals("AVI:JourneyPattern:DY_OSL-SOG-BGO")) {
                DestinationDisplayRefStructure destinationDisplayRef = journeyPattern.getDestinationDisplayRef();
                assertThat(destinationDisplayRef).isNotNull();
                assertThat(destinationDisplayRef.getVersion()).isEqualTo(VERSION_ONE);
                assertThat(destinationDisplayRef.getRef()).isEqualTo("AVI:DestinationDisplay:DY_OSL-SOG-BGO");
            }
            if (journeyPattern.getId().equals("AVI:JourneyPattern:DY_BGO-SOG-OSL")) {
                DestinationDisplayRefStructure destinationDisplayRef = journeyPattern.getDestinationDisplayRef();
                assertThat(destinationDisplayRef).isNotNull();
                assertThat(destinationDisplayRef.getVersion()).isEqualTo(VERSION_ONE);
                assertThat(destinationDisplayRef.getRef()).isEqualTo("AVI:DestinationDisplay:DY_BGO-SOG-OSL");
            }
        }

        // check destination displays
        List<DestinationDisplay> destinationDisplays = serviceFrame.getDestinationDisplays().getDestinationDisplay();

        assertThat(destinationDisplays).extracting("version").contains(VERSION_ONE);

        assertThat(destinationDisplays)
                .hasSize(5)
                .extracting("id")
                .contains("AVI:DestinationDisplay:DY_OSL-SOG-BGO", "AVI:DestinationDisplay:DY_BGO-SOG-OSL",
                        "AVI:DestinationDisplay:DYOSLBGO-OSL", "AVI:DestinationDisplay:DYOSLBGO-SOG", "AVI:DestinationDisplay:DYOSLBGO-BGO");

        for (DestinationDisplay destinationDisplay : destinationDisplays) {
            if (destinationDisplay.getId().equals("AVI:DestinationDisplay:DY_OSL-SOG-BGO")) {
                assertThat(destinationDisplay.getFrontText().getValue()).isEqualTo("Bergen");
                assertThat(destinationDisplay.getVias().getVia()).hasSize(1);
                assertThat(destinationDisplay.getVias().getVia().get(0).getDestinationDisplayRef().getVersion()).isEqualTo(VERSION_ONE);
                assertThat(destinationDisplay.getVias().getVia().get(0).getDestinationDisplayRef().getRef()).isEqualTo("AVI:DestinationDisplay:DYOSLBGO-SOG");
            }
            if (destinationDisplay.getId().equals("AVI:DestinationDisplay:DY_BGO-SOG-OSL")) {
                assertThat(destinationDisplay.getFrontText().getValue()).isEqualTo("Oslo");
                assertThat(destinationDisplay.getVias().getVia()).hasSize(1);
                assertThat(destinationDisplay.getVias().getVia().get(0).getDestinationDisplayRef().getVersion()).isEqualTo(VERSION_ONE);
                assertThat(destinationDisplay.getVias().getVia().get(0).getDestinationDisplayRef().getRef()).isEqualTo("AVI:DestinationDisplay:DYOSLBGO-SOG");
            }
        }
    }

    private void assertValidPublicationDelivery(PublicationDeliveryStructure publicationDelivery, String lineName) {
        assertThat(publicationDelivery).isNotNull();
        assertThat(publicationDelivery.getVersion()).isEqualTo(NETEX_PROFILE_VERSION);
        assertThat(publicationDelivery.getPublicationTimestamp()).isNotNull().isBefore(OffsetDateTime.now());
        assertThat(publicationDelivery.getParticipantRef()).isEqualTo("Avinor");
        assertThat(publicationDelivery.getDescription()).isNotNull();
        assertThat(publicationDelivery.getDescription().getValue()).isEqualTo("Line: " + lineName);
        assertThat(publicationDelivery.getDataObjects()).isNotNull();
    }

    private void assertValidNetwork(Network network, String airlineIata) {
        assertThat(network).isNotNull();
        assertThat(network.getId()).isEqualTo(String.format("AVI:Network:%s", airlineIata));
        assertThat(network.getName().getValue()).isEqualTo(LineDataSetFixture.getAirlineName(airlineIata));
    }

    private void assertValidLine(Line line, LineDataSet lineDataSet) {
        assertThat(line.getId()).isEqualTo(String.format("AVI:Line:%s-%s", lineDataSet.getAirlineIata(), lineDataSet.getLineDesignation()));
        assertThat(line.getName().getValue()).isEqualTo(lineDataSet.getLineName());
        assertThat(line.getTransportMode()).isEqualTo(AllVehicleModesOfTransportEnumeration.AIR);
        assertThat(line.getPublicCode()).isEqualTo(lineDataSet.getLineDesignation());
        assertThat(line.getOperatorRef().getRef()).isEqualTo(String.format("AVI:Operator:%s", lineDataSet.getAirlineIata()));

        Set<String> routeIdRefs = lineDataSet.getFlightRoutes().stream()
                .map(FlightRoute::getRouteDesignation)
                .map(designation -> String.format("AVI:Route:%s_%s", lineDataSet.getAirlineIata(), designation))
                .collect(Collectors.toSet());

        assertThat(line.getRoutes().getRouteRef())
                .hasSize(lineDataSet.getFlightRoutes().size())
                .extracting(RouteRefStructure::getRef)
                .containsExactlyElementsOf(routeIdRefs);
    }

    private void assertValidRoutes(RoutesInFrame_RelStructure routeStruct, LineDataSet lineDataSet, Line line) {
        assertThat(routeStruct).isNotNull();
        List<JAXBElement<? extends LinkSequence_VersionStructure>> routeElements = routeStruct.getRoute_();

        List<Route> routes = routeElements.stream()
                .map(JAXBElement::getValue)
                .map(route -> (Route) route)
                .collect(Collectors.toList());
        assertThat(routes).hasSize(lineDataSet.getFlightRoutes().size());

        Set<String> routeIds = lineDataSet.getFlightRoutes().stream()
                .map(FlightRoute::getRouteDesignation)
                .map(designation -> String.format("AVI:Route:%s_%s", lineDataSet.getAirlineIata(), designation))
                .collect(Collectors.toSet());
        assertThat(routes).extracting(Route::getId).containsOnlyElementsOf(routeIds);

        Set<String> routeNames = lineDataSet.getFlightRoutes().stream()
                .map(FlightRoute::getRouteName)
                .collect(Collectors.toSet());
        assertThat(routes).extracting("name.value").containsOnlyElementsOf(routeNames);

        assertThat(routes).extracting("lineRef.value.ref").contains(line.getId());
    }

    private ScheduledStopover createScheduledStopover(String airportIata, OffsetTime arrivalTime, OffsetTime departureTime) {
        ScheduledStopover scheduledStopover = new ScheduledStopover();
        scheduledStopover.setAirportIATA(airportIata);
        if (arrivalTime != null) {
            scheduledStopover.setArrivalTime(arrivalTime);
        }
        if (departureTime != null) {
            scheduledStopover.setDepartureTime(departureTime);
        }
        return scheduledStopover;
    }

    private List<JAXBElement<? extends Common_VersionFrameStructure>> getDataObjectFrames(PublicationDeliveryStructure publicationDelivery) {
        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = publicationDelivery.getDataObjects().getCompositeFrameOrCommonFrame();
        CompositeFrame compositeFrame = getFrames(CompositeFrame.class, dataObjectFrames).get(0);
        return compositeFrame.getFrames().getCommonFrame();
    }

    public <T> List<T> getFrames(Class<T> clazz, List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames) {
        List<T> foundFrames = new ArrayList<>();

        for (JAXBElement<? extends Common_VersionFrameStructure> frame : dataObjectFrames) {
            if (frame.getValue().getClass().equals(clazz)) {
                foundFrames.add(clazz.cast(frame.getValue()));
            }
        }

        return foundFrames;
    }

}