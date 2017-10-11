package no.rutebanken.extime.converter;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.lang3.tuple.Triple;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import no.rutebanken.extime.model.FlightPredicate;
import no.rutebanken.extime.model.StopVisitType;
import no.rutebanken.extime.util.DateUtils;

public class ScheduledFlightConverterTest {

    private ScheduledFlightConverter clazzUnderTest;

    @Before
    public void setUp() throws Exception {
        clazzUnderTest = new ScheduledFlightConverter();
    }

    @Test
    public void extractNoStopoversFromFlights() throws Exception {
        List<Triple<StopVisitType, String, LocalTime>> triples =
                clazzUnderTest.extractStopoversFromFlights(Collections.emptyList());
        Assertions.assertThat(triples)
                .isNotNull()
                .isEmpty();
    }

    @Test
    public void extractStopoversFromFlights() throws Exception {
        List<Triple<StopVisitType, String, LocalTime>> triples =
                clazzUnderTest.extractStopoversFromFlights(createDummyFlights());

        Assertions.assertThat(triples)
                .isNotNull()
                .isNotEmpty()
                .hasSize(6);

        Assertions.assertThat(triples.get(0).getLeft())
                .isEqualTo(StopVisitType.DEPARTURE);
        Assertions.assertThat(triples.get(0).getMiddle())
                .isEqualTo("BGO");

        Assertions.assertThat(triples.get(5).getLeft())
                .isEqualTo(StopVisitType.ARRIVAL);
        Assertions.assertThat(triples.get(5).getMiddle())
                .isEqualTo("SVG");
    }

    @Test
    public void testIsMultiLegFlightRoute() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", DateUtils.parseDate("2017-01-01"), "HOV",
                        LocalTime.parse("07:00:00"), "SOG", LocalTime.parse("07:30:00")),
                createFlight(1003L, "WF", "149", DateUtils.parseDate("2017-01-01"), "SOG",
                        LocalTime.parse("08:00:00"), "BGO", LocalTime.parse("08:30:00"))
        );

        boolean isMultiLegFlightRoute = clazzUnderTest.isMultiLegFlightRoute(flightLegs);

        Assertions.assertThat(isMultiLegFlightRoute).isTrue();
    }

    @Test
    public void testIsNotMultiLegFlightRoute() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", DateUtils.parseDate("2017-01-01"), "HOV",
                        LocalTime.parse("07:00:00"), "SOG", LocalTime.parse("07:30:00"))
        );

        boolean isMultiLegFlightRoute = clazzUnderTest.isMultiLegFlightRoute(flightLegs);

        Assertions.assertThat(isMultiLegFlightRoute).isFalse();
    }

    @Test
    public void testIsDirectFlightRoute() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", DateUtils.parseDate("2017-01-01"), "HOV",
                        LocalTime.parse("07:00:00"), "SOG", LocalTime.parse("07:30:00"))
        );

        boolean isDirectFlightRoute = clazzUnderTest.isDirectFlightRoute(flightLegs);

        Assertions.assertThat(isDirectFlightRoute).isTrue();
    }

    @Test
    public void testIsNotDirectFlightRoute() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", DateUtils.parseDate("2017-01-01"), "HOV",
                        LocalTime.parse("07:00:00"), "SOG", LocalTime.parse("07:30:00")),
                createFlight(1003L, "WF", "149", DateUtils.parseDate("2017-01-01"), "SOG",
                        LocalTime.parse("08:00:00"), "BGO", LocalTime.parse("08:30:00"))
        );

        boolean isDirectFlightRoute = clazzUnderTest.isDirectFlightRoute(flightLegs);

        Assertions.assertThat(isDirectFlightRoute).isFalse();
    }

    @Test
    public void testAssertNullWhenLegPreviouslyProcessed() throws Exception {
        List<Flight> wf149FlightLegs = generateObjectsFromXml("/xml/wf149.xml", Flights.class).getFlight();
        Map<String, List<Flight>> flightsByDepartureAirport = wf149FlightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));
        Map<String, List<Flight>> flightsByArrivalAirportIata = wf149FlightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getArrivalStation));

        Flight currentFlight = wf149FlightLegs.get(0);
        List<Flight> connectingFlightLegs = clazzUnderTest.findConnectingFlightLegs(
                currentFlight, flightsByDepartureAirport, flightsByArrivalAirportIata, Sets.newHashSet(currentFlight.getId()));

        Assertions.assertThat(connectingFlightLegs)
                .isNotNull()
                .isEmpty();
    }

    @Test
    public void testCollectedFlightLegIds() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", DateUtils.parseDate("2017-01-01"), "HOV",
                        LocalTime.parse("07:00:00"), "SOG", LocalTime.parse("07:30:00")),
                createFlight(1003L, "WF", "149", DateUtils.parseDate("2017-01-01"), "SOG",
                        LocalTime.parse("08:00:00"), "BGO", LocalTime.parse("08:30:00")),
                createFlight(9999L, "SK", "4455", DateUtils.parseDate("2017-01-02"), "TRD",
                        LocalTime.parse("08:00:00"), "OSL", LocalTime.parse("08:30:00")),
                createFlight(8888L, "DY", "8899", DateUtils.parseDate("2017-01-03"), "OSL",
                        LocalTime.parse("08:00:00"), "HOV", LocalTime.parse("08:30:00")),
                createFlight(7777L, "M3", "566", DateUtils.parseDate("2017-01-03"), "BGO",
                        LocalTime.parse("08:00:00"), "TRD", LocalTime.parse("08:30:00"))
        );
        Flight currentFlight = createFlight(1001L, "WF", "149", DateUtils.parseDate("2017-01-01"), "OSL",
                LocalTime.parse("06:00:00"), "HOV", LocalTime.parse("06:30:00"));

        Map<String, List<Flight>> flightsByDepartureAirport = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));

        Map<String, List<Flight>> flightsByArrivalAirportIata = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getArrivalStation));

        HashSet<BigInteger> distinctFlightLegIds = Sets.newHashSet();

        clazzUnderTest.findConnectingFlightLegs(
                currentFlight, flightsByDepartureAirport, flightsByArrivalAirportIata, distinctFlightLegIds);

        Assertions.assertThat(distinctFlightLegIds)
                .isNotNull()
                .isNotEmpty()
                .hasSize(3)
                .containsOnly(BigInteger.valueOf(1001L), BigInteger.valueOf(1002L), BigInteger.valueOf(1003L));
    }

    @Test
    public void testFindConnectingFlightLegsForFirstLeg() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", DateUtils.parseDate("2017-01-01"), "HOV",
                        LocalTime.parse("07:00:00"), "SOG", LocalTime.parse("07:30:00")),
                createFlight(1003L, "WF", "149", DateUtils.parseDate("2017-01-01"), "SOG",
                        LocalTime.parse("08:00:00"), "BGO", LocalTime.parse("08:30:00")),
                createFlight(9999L, "SK", "4455", DateUtils.parseDate("2017-01-02"), "TRD",
                        LocalTime.parse("08:00:00"), "OSL", LocalTime.parse("08:30:00")),
                createFlight(8888L, "DY", "8899", DateUtils.parseDate("2017-01-03"), "OSL",
                        LocalTime.parse("08:00:00"), "HOV", LocalTime.parse("08:30:00")),
                createFlight(7777L, "M3", "566", DateUtils.parseDate("2017-01-03"), "BGO",
                        LocalTime.parse("08:00:00"), "TRD", LocalTime.parse("08:30:00"))
        );
        Flight currentFlight = createFlight(1001L, "WF", "149", DateUtils.parseDate("2017-01-01"), "OSL",
                LocalTime.parse("06:00:00"), "HOV", LocalTime.parse("06:30:00"));

        Map<String, List<Flight>> flightsByDepartureAirport = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));

        Map<String, List<Flight>> flightsByArrivalAirportIata = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getArrivalStation));

        List<Flight> connectingFlightLegs = clazzUnderTest.findConnectingFlightLegs(
                currentFlight, flightsByDepartureAirport, flightsByArrivalAirportIata, Sets.newHashSet());

        Assertions.assertThat(connectingFlightLegs)
                .isNotNull()
                .isNotEmpty()
                .hasSize(3)
                .containsSequence(currentFlight, flightLegs.get(0), flightLegs.get(1));
    }

    @Test
    public void testFindConnectingFlightLegsForLastLeg() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1001L, "WF", "149", DateUtils.parseDate("2017-01-01"), "OSL",
                        LocalTime.parse("06:00:00"), "HOV", LocalTime.parse("06:30:00")),
                createFlight(1002L, "WF", "149", DateUtils.parseDate("2017-01-01"), "HOV",
                        LocalTime.parse("07:00:00"), "SOG", LocalTime.parse("07:30:00")),
                createFlight(9999L, "SK", "4455", DateUtils.parseDate("2017-01-02"), "TRD",
                        LocalTime.parse("08:00:00"), "OSL", LocalTime.parse("08:30:00")),
                createFlight(8888L, "DY", "8899", DateUtils.parseDate("2017-01-03"), "OSL",
                        LocalTime.parse("08:00:00"), "HOV", LocalTime.parse("08:30:00")),
                createFlight(7777L, "M3", "566", DateUtils.parseDate("2017-01-03"), "BGO",
                        LocalTime.parse("08:00:00"), "TRD", LocalTime.parse("08:30:00"))
        );
        Flight currentFlight = createFlight(1003L, "WF", "149", DateUtils.parseDate("2017-01-01"), "SOG",
                LocalTime.parse("08:00:00"), "BGO", LocalTime.parse("08:30:00"));

        Map<String, List<Flight>> flightsByDepartureAirport = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));

        Map<String, List<Flight>> flightsByArrivalAirportIata = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getArrivalStation));

        List<Flight> connectingFlightLegs = clazzUnderTest.findConnectingFlightLegs(
                currentFlight, flightsByDepartureAirport, flightsByArrivalAirportIata, Sets.newHashSet());

        Assertions.assertThat(connectingFlightLegs)
                .isNotNull()
                .isNotEmpty()
                .hasSize(3)
                .containsSequence(flightLegs.get(0), flightLegs.get(1), currentFlight);
    }

    @Test
    public void testFindConnectingFlightLegsForMiddleLeg() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1001L, "WF", "149", DateUtils.parseDate("2017-01-01"), "OSL",
                        LocalTime.parse("06:00:00"), "HOV", LocalTime.parse("06:30:00")),
                createFlight(1003L, "WF", "149", DateUtils.parseDate("2017-01-01"), "SOG",
                        LocalTime.parse("08:00:00"), "BGO", LocalTime.parse("08:30:00")),
                createFlight(9999L, "SK", "4455", DateUtils.parseDate("2017-01-02"), "TRD",
                        LocalTime.parse("08:00:00"), "OSL", LocalTime.parse("08:30:00")),
                createFlight(8888L, "DY", "8899", DateUtils.parseDate("2017-01-03"), "OSL",
                        LocalTime.parse("08:00:00"), "HOV", LocalTime.parse("08:30:00")),
                createFlight(7777L, "M3", "566", DateUtils.parseDate("2017-01-03"), "BGO",
                        LocalTime.parse("08:00:00"), "TRD", LocalTime.parse("08:30:00"))
        );
        Flight currentFlight = createFlight(1002L, "WF", "149", DateUtils.parseDate("2017-01-01"), "HOV",
                LocalTime.parse("07:00:00"), "SOG", LocalTime.parse("07:30:00"));

        Map<String, List<Flight>> flightsByDepartureAirport = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));

        Map<String, List<Flight>> flightsByArrivalAirportIata = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getArrivalStation));

        List<Flight> connectingFlightLegs = clazzUnderTest.findConnectingFlightLegs(
                currentFlight, flightsByDepartureAirport, flightsByArrivalAirportIata, Sets.newHashSet());

        Assertions.assertThat(connectingFlightLegs)
                .isNotNull()
                .isNotEmpty()
                .hasSize(3)
                .containsSequence(flightLegs.get(0), currentFlight, flightLegs.get(1));
    }

    @Test
    public void testFindConnectingFlightLegsForArbitraryLeg() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1001L, "WF", "199", DateUtils.parseDate("2017-01-01"), "OSL",
                        LocalTime.parse("06:00:00"), "TRD", LocalTime.parse("06:30:00")),
                createFlight(1002L, "WF", "199", DateUtils.parseDate("2017-01-01"), "TRD",
                        LocalTime.parse("07:00:00"), "BGO", LocalTime.parse("07:30:00")),
                createFlight(1003L, "WF", "199", DateUtils.parseDate("2017-01-01"), "BGO",
                        LocalTime.parse("08:00:00"), "HOV", LocalTime.parse("08:30:00")),
                createFlight(1005L, "WF", "199", DateUtils.parseDate("2017-01-01"), "SOG",
                        LocalTime.parse("10:00:00"), "TOS", LocalTime.parse("10:30:00")),
                createFlight(1006L, "WF", "199", DateUtils.parseDate("2017-01-01"), "TOS",
                        LocalTime.parse("11:00:00"), "EVE", LocalTime.parse("11:30:00")),
                createFlight(9999L, "SK", "4455", DateUtils.parseDate("2017-01-02"), "TRD",
                        LocalTime.parse("08:00:00"), "OSL", LocalTime.parse("08:30:00")),
                createFlight(8888L, "DY", "8899", DateUtils.parseDate("2017-01-03"), "OSL",
                        LocalTime.parse("08:00:00"), "HOV", LocalTime.parse("08:30:00")),
                createFlight(7777L, "M3", "566", DateUtils.parseDate("2017-01-03"), "BGO",
                        LocalTime.parse("08:00:00"), "TRD", LocalTime.parse("08:30:00")),
                createFlight(7766L, "DY", "444", DateUtils.parseDate("2017-01-03"), "TOS",
                        LocalTime.parse("08:00:00"), "EVE", LocalTime.parse("08:30:00")),
                createFlight(7766L, "DY", "333", DateUtils.parseDate("2017-01-04"), "EVE",
                        LocalTime.parse("08:00:00"), "TOS", LocalTime.parse("08:30:00"))
        );
        Flight currentFlight = createFlight(1004L, "WF", "199", DateUtils.parseDate("2017-01-01"), "HOV",
                LocalTime.parse("09:00:00"), "SOG", LocalTime.parse("09:30:00"));

        Map<String, List<Flight>> flightsByDepartureAirport = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));

        Map<String, List<Flight>> flightsByArrivalAirportIata = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getArrivalStation));

        List<Flight> connectingFlightLegs = clazzUnderTest.findConnectingFlightLegs(
                currentFlight, flightsByDepartureAirport, flightsByArrivalAirportIata, Sets.newHashSet());

        Assertions.assertThat(connectingFlightLegs)
                .isNotNull()
                .isNotEmpty()
                .hasSize(6)
                .containsSequence(flightLegs.get(0), flightLegs.get(1), flightLegs.get(2),
                        currentFlight, flightLegs.get(3), flightLegs.get(4));
    }

    @Test
    public void testFindNextFlightLegsForLastLeg() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1099L, "SK", "4455", DateUtils.parseDate("2017-01-30"), "BGO",
                        LocalTime.parse("07:00:00"), "OSL", LocalTime.parse("07:30:00"))
        );
        Flight currentFlight = createFlight(1003L, "WF", "149", DateUtils.parseDate("2017-01-01"), "SOG",
                LocalTime.parse("06:00:00"), "BGO", LocalTime.parse("06:30:00"));

        Map<String, List<Flight>> flightsByDepartureAirport = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));

        List<Flight> nextFlightLegs = clazzUnderTest.findNextFlightLegs(
                currentFlight, flightsByDepartureAirport, Lists.newLinkedList());

        Assertions.assertThat(nextFlightLegs)
                .isNotNull()
                .isEmpty();
    }

    @Test
    public void testFindNextFlightLegs() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", DateUtils.parseDate("2017-01-01"), "HOV",
                        LocalTime.parse("09:00:00"), "SOG", LocalTime.parse("09:30:00")),
                createFlight(1003L, "WF", "149", DateUtils.parseDate("2017-01-01"), "SOG",
                        LocalTime.parse("10:00:00"), "BGO", LocalTime.parse("10:30:00")),
                createFlight(1004L, "WF", "148", DateUtils.parseDate("2017-01-02"), "BGO",
                        LocalTime.parse("06:00:00"), "SOG", LocalTime.parse("06:30:00"))
        );
        Flight currentFlight = createFlight(1001L, "WF", "149", DateUtils.parseDate("2017-01-01"), "OSL",
                LocalTime.parse("08:00:00"), "HOV", LocalTime.parse("08:30:00"));

        Map<String, List<Flight>> flightsByDepartureAirport = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));

        List<Flight> nextFlightLegs = clazzUnderTest.findNextFlightLegs(
                currentFlight, flightsByDepartureAirport, Lists.newLinkedList());

        Assertions.assertThat(nextFlightLegs)
                .isNotNull()
                .isNotEmpty()
                .hasSize(2)
                .containsOnly(flightLegs.get(0), flightLegs.get(1));
    }
    @Test
    public void testFindPreviousFlightLegsForFirstLeg() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1099L, "SK", "4455", DateUtils.parseDate("2017-01-30"), "TRD",
                        LocalTime.parse("07:00:00"), "OSL", LocalTime.parse("07:30:00"))
        );
        Flight currentFlight = createFlight(1001L, "WF", "149", DateUtils.parseDate("2017-01-01"), "OSL",
                LocalTime.parse("06:00:00"), "HOV", LocalTime.parse("06:30:00"));

        Map<String, List<Flight>> flightsByArrivalAirportIata = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getArrivalStation));

        List<Flight> previousFlightLegs = clazzUnderTest.findPreviousFlightLegs(
                currentFlight, flightsByArrivalAirportIata, Lists.newLinkedList());

        Assertions.assertThat(previousFlightLegs)
                .isNotNull()
                .isEmpty();
    }

    @Test
    public void testFindPreviousFlightLegs() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1001L, "WF", "149", DateUtils.parseDate("2017-01-01"), "OSL",
                        LocalTime.parse("06:00:00"), "HOV", LocalTime.parse("06:30:00")),
                createFlight(1002L, "WF", "149", DateUtils.parseDate("2017-01-01"), "HOV",
                        LocalTime.parse("07:00:00"), "SOG", LocalTime.parse("07:30:00")),
                createFlight(1004L, "WF", "148", DateUtils.parseDate("2017-01-02"), "HOV",
                        LocalTime.parse("07:00:00"), "OSL", LocalTime.parse("07:30:00"))
        );
        Flight currentFlight = createFlight(1003L, "WF", "149", DateUtils.parseDate("2017-01-01"), "SOG",
                LocalTime.parse("08:00:00"), "BGO", LocalTime.parse("08:30:00"));

        Map<String, List<Flight>> flightsByArrivalAirportIata = flightLegs.stream()
                .collect(Collectors.groupingBy(Flight::getArrivalStation));

        List<Flight> previousFlightLegs = clazzUnderTest.findPreviousFlightLegs(
                currentFlight, flightsByArrivalAirportIata, Lists.newLinkedList());

        Assertions.assertThat(previousFlightLegs)
                .isNotNull()
                .isNotEmpty()
                .hasSize(2)
                .containsOnly(flightLegs.get(0), flightLegs.get(1));
    }

    @Test
    public void testFindOptionalConnectingFlightLeg() throws Exception {
        Flight currentFlightLeg = createFlight(1003L, "WF", "149", DateUtils.parseDate("2017-01-01"),
                "SOG", LocalTime.parse("09:00:00"), "BGO", LocalTime.parse("09:30:00"));
        Predicate<Flight> previousFlightPredicate = FlightPredicate.matchPreviousFlight(currentFlightLeg);

        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", DateUtils.parseDate("2017-01-01"), "HOV",
                        LocalTime.parse("08:00:00"), "SOG", LocalTime.parse("08:30:00"))
        );

        Optional<Flight> optionalFlight = clazzUnderTest.findOptionalConnectingFlightLeg(previousFlightPredicate, flightLegs);

        Assertions.assertThat(optionalFlight).isPresent();

        optionalFlight.ifPresent(flight -> Assertions.assertThat(flight)
                .isNotNull()
                .isEqualTo(flightLegs.get(0)));
    }

    @Test
    public void testDoNotFindOptionalConnectingFlightLeg() throws Exception {
        Flight currentFlightLeg = createFlight(1001L, "WF", "149", DateUtils.parseDate("2017-01-01"),
                "OSL", LocalTime.parse("07:00:00"), "HOV", LocalTime.parse("07:30:00"));
        Predicate<Flight> previousFlightPredicate = FlightPredicate.matchPreviousFlight(currentFlightLeg);

        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1003L, "WF", "149", DateUtils.parseDate("2017-01-01"), "SOG",
                        LocalTime.parse("08:00:00"), "BGO", LocalTime.parse("08:30:00"))
        );

        Optional<Flight> optionalFlight = clazzUnderTest.findOptionalConnectingFlightLeg(previousFlightPredicate, flightLegs);

        Assertions.assertThat(optionalFlight)
                .isNotNull()
                .isEmpty();
    }

    private List<Flight> createDummyFlights() {
        return Lists.newArrayList(
                createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO", LocalTime.MIN, "OSL", LocalTime.MAX),
                createFlight(2L, "DY", "6677", DateUtils.parseDate("2017-01-02"), "BGO", LocalTime.MIN, "TRD", LocalTime.MAX),
                createFlight(3L, "WF", "199", DateUtils.parseDate("2017-01-03"), "BGO", LocalTime.MIN, "SVG", LocalTime.MAX)
        );
    }

    private Flight createFlight(long id, String designator, String flightNumber, ZonedDateTime dateOfOperation,
                                String departureStation, LocalTime departureTime, String arrivalStation, LocalTime arrivalTime) {
        Flight flight = new Flight();
        flight.setId(BigInteger.valueOf(id));
        flight.setAirlineDesignator(designator);
        flight.setFlightNumber(flightNumber);
        flight.setDateOfOperation(dateOfOperation);
        flight.setDepartureStation(departureStation);
        flight.setStd(departureTime);
        flight.setArrivalStation(arrivalStation);
        flight.setSta(arrivalTime);
        return flight;
    }

    private <T> T generateObjectsFromXml(String resourceName, Class<T> clazz) throws JAXBException {
        return JAXBContext.newInstance(clazz).createUnmarshaller().unmarshal(
                new StreamSource(getClass().getResourceAsStream(resourceName)), clazz).getValue();
    }
}