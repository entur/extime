package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.FlightPredicate;
import no.rutebanken.extime.model.ScheduledFlight;
import no.rutebanken.extime.model.StopVisitType;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ScheduledFlightConverterTest {

    private ScheduledFlightConverter clazzUnderTest;

    @Before
    public void setUp() throws Exception {
        clazzUnderTest = new ScheduledFlightConverter();
    }

    @Test
    @Ignore
    public void convertFlights() throws Exception {
        AirportIATA[] airportIATAs = Arrays.stream(AirportIATA.values())
                .filter(iata -> !iata.equals(AirportIATA.OSL))
                .toArray(AirportIATA[]::new);
        ArrayList<Flight> masterFlights = Lists.newArrayList();
        for (int i = 1; i <= 2; i++) {
            for (AirportIATA airportIATA : airportIATAs) {
                String resourceName = String.format("%s-%d.xml", airportIATA, i);
                Flights flightStructure = generateObjectsFromXml(String.format("/xml/testdata/%s", resourceName), Flights.class);
                List<Flight> flights = flightStructure.getFlight();
                masterFlights.addAll(flights);
            }
        }
        ArrayList<Flight> finalList = Lists.newArrayList();
        for (Flight flight : masterFlights) {
            for (StopVisitType stopVisitType : StopVisitType.values()) {
                if (isValidFlight(stopVisitType, flight)) {
                    finalList.add(flight);
                }
            }
        }
        List<ScheduledFlight> scheduledFlights = clazzUnderTest.convertToScheduledFlights(finalList);
        Assertions.assertThat(scheduledFlights).isNotNull();
    }

    @Test
    @Ignore
    public void convertStopoverFlights() throws Exception {
        ScheduledFlight expectedWF148Flight = new ScheduledFlight();
        expectedWF148Flight.setAirlineIATA("WF");
        expectedWF148Flight.setAirlineFlightId("WF148");
        expectedWF148Flight.setDateOfOperation(LocalDate.parse("2016-08-16"));
        expectedWF148Flight.setRouteString("BGO-SOG-HOV-OSL");

        ScheduledFlight expectedWF149Flight = new ScheduledFlight();
        expectedWF149Flight.setAirlineIATA("WF");
        expectedWF149Flight.setAirlineFlightId("WF149");
        expectedWF149Flight.setDateOfOperation(LocalDate.parse("2016-08-17"));

        Flights flights = generateObjectsFromXml("/xml/wf148-wf149.xml", Flights.class);
        List<ScheduledFlight> scheduledFlights = clazzUnderTest.convertToScheduledFlights(flights.getFlight());

        Assertions.assertThat(scheduledFlights)
                .isNotNull()
                .isNotEmpty()
                .hasSize(3);

        String[] fieldsToCompare = {"airlineIATA", "airlineFlightId", "dateOfOperation"};

        Assertions.assertThat(scheduledFlights.get(0))
                .isExactlyInstanceOf(ScheduledFlight.class)
                .isEqualToComparingOnlyGivenFields(expectedWF148Flight, fieldsToCompare);
        Assertions.assertThat(((ScheduledFlight) scheduledFlights.get(0)).getScheduledStopovers())
                .isNotNull()
                .isNotEmpty()
                .hasSize(4);

        Assertions.assertThat(scheduledFlights.get(1))
                .isExactlyInstanceOf(ScheduledFlight.class)
                .isEqualToComparingOnlyGivenFields(expectedWF149Flight, fieldsToCompare);
        Assertions.assertThat(((ScheduledFlight) scheduledFlights.get(1)).getScheduledStopovers())
                .isNotNull()
                .isNotEmpty()
                .hasSize(4);
    }

    @Test
    public void extractNoStopoversFromFlights() throws Exception {
        List<Triple<StopVisitType, String, OffsetTime>> triples =
                clazzUnderTest.extractStopoversFromFlights(Collections.emptyList());
        Assertions.assertThat(triples)
                .isNotNull()
                .isEmpty();
    }

    @Test
    public void extractStopoversFromFlights() throws Exception {
        List<Triple<StopVisitType, String, OffsetTime>> triples =
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
    @Ignore
    public void convertFlightToScheduledDirectFlight() throws Exception {
        Flight dummyFlight = createFlight(1L, "SK", "4455",
                LocalDate.parse("2017-01-01"), "BGO", OffsetTime.MIN, "OSL", OffsetTime.MAX);

        ScheduledFlight directFlight = clazzUnderTest.convertToScheduledFlight(dummyFlight, OffsetDateTime.MIN, OffsetDateTime.MAX);

        Assertions.assertThat(directFlight)
                .isNotNull()
                .isInstanceOf(ScheduledFlight.class);
        Assertions.assertThat(directFlight.getFlightId())
                .isNotNull()
                .isEqualTo(dummyFlight.getId());
        Assertions.assertThat(directFlight.getAirlineIATA())
                .isNotNull()
                .isEqualTo(dummyFlight.getAirlineDesignator());
        Assertions.assertThat(directFlight.getAirlineFlightId())
                .isNotNull()
                .isEqualTo(String.format("%s%s", dummyFlight.getAirlineDesignator(), dummyFlight.getFlightNumber()));
        Assertions.assertThat(directFlight.getDateOfOperation())
                .isNotNull()
                .isEqualTo(dummyFlight.getDateOfOperation());
        Assertions.assertThat(directFlight.getDepartureAirportIATA())
                .isNotNull()
                .isEqualTo(dummyFlight.getDepartureStation());
        Assertions.assertThat(directFlight.getArrivalAirportIATA())
                .isNotNull()
                .isEqualTo(dummyFlight.getArrivalStation());
        Assertions.assertThat(directFlight.getTimeOfDeparture())
                .isNotNull()
                .isEqualTo(dummyFlight.getStd());
        Assertions.assertThat(directFlight.getTimeOfArrival())
                .isNotNull()
                .isEqualTo(dummyFlight.getSta());
    }

    @Test
    public void testIsMultiLegFlightRoute() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", LocalDate.parse("2017-01-01"), "HOV",
                        OffsetTime.parse("07:00:00Z"), "SOG", OffsetTime.parse("07:30:00Z")),
                createFlight(1003L, "WF", "149", LocalDate.parse("2017-01-01"), "SOG",
                        OffsetTime.parse("08:00:00Z"), "BGO", OffsetTime.parse("08:30:00Z"))
        );

        boolean isMultiLegFlightRoute = clazzUnderTest.isMultiLegFlightRoute(flightLegs);

        Assertions.assertThat(isMultiLegFlightRoute).isTrue();
    }

    @Test
    public void testIsNotMultiLegFlightRoute() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", LocalDate.parse("2017-01-01"), "HOV",
                        OffsetTime.parse("07:00:00Z"), "SOG", OffsetTime.parse("07:30:00Z"))
        );

        boolean isMultiLegFlightRoute = clazzUnderTest.isMultiLegFlightRoute(flightLegs);

        Assertions.assertThat(isMultiLegFlightRoute).isFalse();
    }

    @Test
    public void testIsDirectFlightRoute() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", LocalDate.parse("2017-01-01"), "HOV",
                        OffsetTime.parse("07:00:00Z"), "SOG", OffsetTime.parse("07:30:00Z"))
        );

        boolean isDirectFlightRoute = clazzUnderTest.isDirectFlightRoute(flightLegs);

        Assertions.assertThat(isDirectFlightRoute).isTrue();
    }

    @Test
    public void testIsNotDirectFlightRoute() throws Exception {
        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", LocalDate.parse("2017-01-01"), "HOV",
                        OffsetTime.parse("07:00:00Z"), "SOG", OffsetTime.parse("07:30:00Z")),
                createFlight(1003L, "WF", "149", LocalDate.parse("2017-01-01"), "SOG",
                        OffsetTime.parse("08:00:00Z"), "BGO", OffsetTime.parse("08:30:00Z"))
        );

        boolean isDirectFlightRoute = clazzUnderTest.isDirectFlightRoute(flightLegs);

        Assertions.assertThat(isDirectFlightRoute).isFalse();
    }

    @Test
    public void testCreateScheduledStopovers() throws Exception {
    }

    @Test
    public void testCreateScheduledStopoverFlight() throws Exception {
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
                createFlight(1002L, "WF", "149", LocalDate.parse("2017-01-01"), "HOV",
                        OffsetTime.parse("07:00:00Z"), "SOG", OffsetTime.parse("07:30:00Z")),
                createFlight(1003L, "WF", "149", LocalDate.parse("2017-01-01"), "SOG",
                        OffsetTime.parse("08:00:00Z"), "BGO", OffsetTime.parse("08:30:00Z")),
                createFlight(9999L, "SK", "4455", LocalDate.parse("2017-01-02"), "TRD",
                        OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z")),
                createFlight(8888L, "DY", "8899", LocalDate.parse("2017-01-03"), "OSL",
                        OffsetTime.parse("08:00:00Z"), "HOV", OffsetTime.parse("08:30:00Z")),
                createFlight(7777L, "M3", "566", LocalDate.parse("2017-01-03"), "BGO",
                        OffsetTime.parse("08:00:00Z"), "TRD", OffsetTime.parse("08:30:00Z"))
        );
        Flight currentFlight = createFlight(1001L, "WF", "149", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("06:00:00Z"), "HOV", OffsetTime.parse("06:30:00Z"));

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
                createFlight(1002L, "WF", "149", LocalDate.parse("2017-01-01"), "HOV",
                        OffsetTime.parse("07:00:00Z"), "SOG", OffsetTime.parse("07:30:00Z")),
                createFlight(1003L, "WF", "149", LocalDate.parse("2017-01-01"), "SOG",
                        OffsetTime.parse("08:00:00Z"), "BGO", OffsetTime.parse("08:30:00Z")),
                createFlight(9999L, "SK", "4455", LocalDate.parse("2017-01-02"), "TRD",
                        OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z")),
                createFlight(8888L, "DY", "8899", LocalDate.parse("2017-01-03"), "OSL",
                        OffsetTime.parse("08:00:00Z"), "HOV", OffsetTime.parse("08:30:00Z")),
                createFlight(7777L, "M3", "566", LocalDate.parse("2017-01-03"), "BGO",
                        OffsetTime.parse("08:00:00Z"), "TRD", OffsetTime.parse("08:30:00Z"))
        );
        Flight currentFlight = createFlight(1001L, "WF", "149", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("06:00:00Z"), "HOV", OffsetTime.parse("06:30:00Z"));

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
                createFlight(1001L, "WF", "149", LocalDate.parse("2017-01-01"), "OSL",
                        OffsetTime.parse("06:00:00Z"), "HOV", OffsetTime.parse("06:30:00Z")),
                createFlight(1002L, "WF", "149", LocalDate.parse("2017-01-01"), "HOV",
                        OffsetTime.parse("07:00:00Z"), "SOG", OffsetTime.parse("07:30:00Z")),
                createFlight(9999L, "SK", "4455", LocalDate.parse("2017-01-02"), "TRD",
                        OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z")),
                createFlight(8888L, "DY", "8899", LocalDate.parse("2017-01-03"), "OSL",
                        OffsetTime.parse("08:00:00Z"), "HOV", OffsetTime.parse("08:30:00Z")),
                createFlight(7777L, "M3", "566", LocalDate.parse("2017-01-03"), "BGO",
                        OffsetTime.parse("08:00:00Z"), "TRD", OffsetTime.parse("08:30:00Z"))
        );
        Flight currentFlight = createFlight(1003L, "WF", "149", LocalDate.parse("2017-01-01"), "SOG",
                OffsetTime.parse("08:00:00Z"), "BGO", OffsetTime.parse("08:30:00Z"));

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
                createFlight(1001L, "WF", "149", LocalDate.parse("2017-01-01"), "OSL",
                        OffsetTime.parse("06:00:00Z"), "HOV", OffsetTime.parse("06:30:00Z")),
                createFlight(1003L, "WF", "149", LocalDate.parse("2017-01-01"), "SOG",
                        OffsetTime.parse("08:00:00Z"), "BGO", OffsetTime.parse("08:30:00Z")),
                createFlight(9999L, "SK", "4455", LocalDate.parse("2017-01-02"), "TRD",
                        OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z")),
                createFlight(8888L, "DY", "8899", LocalDate.parse("2017-01-03"), "OSL",
                        OffsetTime.parse("08:00:00Z"), "HOV", OffsetTime.parse("08:30:00Z")),
                createFlight(7777L, "M3", "566", LocalDate.parse("2017-01-03"), "BGO",
                        OffsetTime.parse("08:00:00Z"), "TRD", OffsetTime.parse("08:30:00Z"))
        );
        Flight currentFlight = createFlight(1002L, "WF", "149", LocalDate.parse("2017-01-01"), "HOV",
                OffsetTime.parse("07:00:00Z"), "SOG", OffsetTime.parse("07:30:00Z"));

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
                createFlight(1001L, "WF", "199", LocalDate.parse("2017-01-01"), "OSL",
                        OffsetTime.parse("06:00:00Z"), "TRD", OffsetTime.parse("06:30:00Z")),
                createFlight(1002L, "WF", "199", LocalDate.parse("2017-01-01"), "TRD",
                        OffsetTime.parse("07:00:00Z"), "BGO", OffsetTime.parse("07:30:00Z")),
                createFlight(1003L, "WF", "199", LocalDate.parse("2017-01-01"), "BGO",
                        OffsetTime.parse("08:00:00Z"), "HOV", OffsetTime.parse("08:30:00Z")),
                createFlight(1005L, "WF", "199", LocalDate.parse("2017-01-01"), "SOG",
                        OffsetTime.parse("10:00:00Z"), "TOS", OffsetTime.parse("10:30:00Z")),
                createFlight(1006L, "WF", "199", LocalDate.parse("2017-01-01"), "TOS",
                        OffsetTime.parse("11:00:00Z"), "EVE", OffsetTime.parse("11:30:00Z")),
                createFlight(9999L, "SK", "4455", LocalDate.parse("2017-01-02"), "TRD",
                        OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z")),
                createFlight(8888L, "DY", "8899", LocalDate.parse("2017-01-03"), "OSL",
                        OffsetTime.parse("08:00:00Z"), "HOV", OffsetTime.parse("08:30:00Z")),
                createFlight(7777L, "M3", "566", LocalDate.parse("2017-01-03"), "BGO",
                        OffsetTime.parse("08:00:00Z"), "TRD", OffsetTime.parse("08:30:00Z")),
                createFlight(7766L, "DY", "444", LocalDate.parse("2017-01-03"), "TOS",
                        OffsetTime.parse("08:00:00Z"), "EVE", OffsetTime.parse("08:30:00Z")),
                createFlight(7766L, "DY", "333", LocalDate.parse("2017-01-04"), "EVE",
                        OffsetTime.parse("08:00:00Z"), "TOS", OffsetTime.parse("08:30:00Z"))
        );
        Flight currentFlight = createFlight(1004L, "WF", "199", LocalDate.parse("2017-01-01"), "HOV",
                OffsetTime.parse("09:00:00Z"), "SOG", OffsetTime.parse("09:30:00Z"));

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
                createFlight(1099L, "SK", "4455", LocalDate.parse("2017-01-30"), "BGO",
                        OffsetTime.parse("07:00:00Z"), "OSL", OffsetTime.parse("07:30:00Z"))
        );
        Flight currentFlight = createFlight(1003L, "WF", "149", LocalDate.parse("2017-01-01"), "SOG",
                OffsetTime.parse("06:00:00Z"), "BGO", OffsetTime.parse("06:30:00Z"));

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
                createFlight(1002L, "WF", "149", LocalDate.parse("2017-01-01"), "HOV",
                        OffsetTime.parse("09:00:00Z"), "SOG", OffsetTime.parse("09:30:00Z")),
                createFlight(1003L, "WF", "149", LocalDate.parse("2017-01-01"), "SOG",
                        OffsetTime.parse("10:00:00Z"), "BGO", OffsetTime.parse("10:30:00Z")),
                createFlight(1004L, "WF", "148", LocalDate.parse("2017-01-02"), "BGO",
                        OffsetTime.parse("06:00:00Z"), "SOG", OffsetTime.parse("06:30:00Z"))
        );
        Flight currentFlight = createFlight(1001L, "WF", "149", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("08:00:00Z"), "HOV", OffsetTime.parse("08:30:00Z"));

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
                createFlight(1099L, "SK", "4455", LocalDate.parse("2017-01-30"), "TRD",
                        OffsetTime.parse("07:00:00Z"), "OSL", OffsetTime.parse("07:30:00Z"))
        );
        Flight currentFlight = createFlight(1001L, "WF", "149", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("06:00:00Z"), "HOV", OffsetTime.parse("06:30:00Z"));

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
                createFlight(1001L, "WF", "149", LocalDate.parse("2017-01-01"), "OSL",
                        OffsetTime.parse("06:00:00Z"), "HOV", OffsetTime.parse("06:30:00Z")),
                createFlight(1002L, "WF", "149", LocalDate.parse("2017-01-01"), "HOV",
                        OffsetTime.parse("07:00:00Z"), "SOG", OffsetTime.parse("07:30:00Z")),
                createFlight(1004L, "WF", "148", LocalDate.parse("2017-01-02"), "HOV",
                        OffsetTime.parse("07:00:00Z"), "OSL", OffsetTime.parse("07:30:00Z"))
        );
        Flight currentFlight = createFlight(1003L, "WF", "149", LocalDate.parse("2017-01-01"), "SOG",
                OffsetTime.parse("08:00:00Z"), "BGO", OffsetTime.parse("08:30:00Z"));

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
        Flight currentFlightLeg = createFlight(1003L, "WF", "149", LocalDate.parse("2017-01-01"),
                "SOG", OffsetTime.parse("09:00:00Z"), "BGO", OffsetTime.parse("09:30:00Z"));
        Predicate<Flight> previousFlightPredicate = FlightPredicate.matchPreviousFlight(currentFlightLeg);

        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", LocalDate.parse("2017-01-01"), "HOV",
                        OffsetTime.parse("08:00:00Z"), "SOG", OffsetTime.parse("08:30:00Z"))
        );

        Flight optionalFlight = clazzUnderTest.findOptionalConnectingFlightLeg(previousFlightPredicate, flightLegs);

        Assertions.assertThat(optionalFlight)
                .isNotNull()
                .isEqualTo(flightLegs.get(0));
    }

    @Test
    public void testDoNotFindOptionalConnectingFlightLeg() throws Exception {
        Flight currentFlightLeg = createFlight(1001L, "WF", "149", LocalDate.parse("2017-01-01"),
                "OSL", OffsetTime.parse("07:00:00Z"), "HOV", OffsetTime.parse("07:30:00Z"));
        Predicate<Flight> previousFlightPredicate = FlightPredicate.matchPreviousFlight(currentFlightLeg);

        List<Flight> flightLegs = Lists.newArrayList(
                createFlight(1003L, "WF", "149", LocalDate.parse("2017-01-01"), "SOG",
                        OffsetTime.parse("08:00:00Z"), "BGO", OffsetTime.parse("08:30:00Z"))
        );

        Flight optionalFlight = clazzUnderTest.findOptionalConnectingFlightLeg(previousFlightPredicate, flightLegs);

        Assertions.assertThat(optionalFlight)
                .isNull();
    }

    private List<Flight> createDummyFlights() {
        return Lists.newArrayList(
                createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO", OffsetTime.MIN, "OSL", OffsetTime.MAX),
                createFlight(2L, "DY", "6677", LocalDate.parse("2017-01-02"), "BGO", OffsetTime.MIN, "TRD", OffsetTime.MAX),
                createFlight(3L, "WF", "199", LocalDate.parse("2017-01-03"), "BGO", OffsetTime.MIN, "SVG", OffsetTime.MAX)
        );
    }

    private Flight createFlight(long id, String designator, String flightNumber, LocalDate dateOfOperation,
                                String departureStation, OffsetTime departureTime, String arrivalStation, OffsetTime arrivalTime) {
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

    // @todo: refactor! - duplicate in AvinorTimetableUtils (make static)
    private boolean isValidFlight(StopVisitType stopVisitType, Flight newFlight) {
        switch (stopVisitType) {
            case ARRIVAL:
                return AirportIATA.OSL.name().equalsIgnoreCase(newFlight.getDepartureStation());
            case DEPARTURE:
                return isDomesticFlight(newFlight);
        }
        return false;
    }

    // @todo: refactor! - duplicate in AvinorTimetableUtils (make static)
    private boolean isDomesticFlight(Flight flight) {
        return isValidDepartureAndArrival(flight.getDepartureStation(), flight.getArrivalStation());
    }

    // @todo: refactor! - duplicate in AvinorTimetableUtils (make static)
    private boolean isValidDepartureAndArrival(String departureIATA, String arrivalIATA) {
        return EnumUtils.isValidEnum(AirportIATA.class, departureIATA)
                && EnumUtils.isValidEnum(AirportIATA.class, arrivalIATA);
    }

    // @todo: refactor! - duplicate in AvinorTimetableUtils (make static)
    private <T> T generateObjectsFromXml(String resourceName, Class<T> clazz) throws JAXBException {
        return JAXBContext.newInstance(clazz).createUnmarshaller().unmarshal(
                new StreamSource(getClass().getResourceAsStream(resourceName)), clazz).getValue();
    }
}