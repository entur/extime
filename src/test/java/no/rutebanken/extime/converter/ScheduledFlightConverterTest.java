package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import no.rutebanken.extime.model.ScheduledDirectFlight;
import no.rutebanken.extime.model.ScheduledFlight;
import no.rutebanken.extime.model.ScheduledStopoverFlight;
import no.rutebanken.extime.model.StopVisitType;
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
import java.time.LocalTime;
import java.util.*;
import java.util.function.Predicate;

public class ScheduledFlightConverterTest {

    private ScheduledFlightConverter clazzUnderTest;

    @Before
    public void setUp() throws Exception {
        clazzUnderTest = new ScheduledFlightConverter();
    }

    @Test
    public void convertStopoverFlights() throws Exception {
        ScheduledStopoverFlight expectedWF148Flight = new ScheduledStopoverFlight();
        expectedWF148Flight.setAirlineIATA("WF");
        expectedWF148Flight.setAirlineFlightId("WF148");
        expectedWF148Flight.setDateOfOperation(LocalDate.parse("2016-08-16"));
        expectedWF148Flight.setRouteString("BGO-SOG-HOV-OSL");

        ScheduledFlight expectedWF149Flight = new ScheduledStopoverFlight();
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
                .isExactlyInstanceOf(ScheduledStopoverFlight.class)
                .isEqualToComparingOnlyGivenFields(expectedWF148Flight, fieldsToCompare);
        Assertions.assertThat(((ScheduledStopoverFlight) scheduledFlights.get(0)).getScheduledStopovers())
                .isNotNull()
                .isNotEmpty()
                .hasSize(4);

        Assertions.assertThat(scheduledFlights.get(1))
                .isExactlyInstanceOf(ScheduledStopoverFlight.class)
                .isEqualToComparingOnlyGivenFields(expectedWF149Flight, fieldsToCompare);
        Assertions.assertThat(((ScheduledStopoverFlight) scheduledFlights.get(1)).getScheduledStopovers())
                .isNotNull()
                .isNotEmpty()
                .hasSize(4);
    }

    @Test
    @Ignore
    public void convertFlightWithOnceAWeekPattern() throws Exception {
        // @todo: use flight DY743 from TRD-OSL
    }

    @Test
    @Ignore
    public void convertFlightWeeklyWorkDayPattern() throws Exception {
    }

    @Test
    @Ignore
    public void convertFlightWithWeeklyGapPattern() throws Exception {
        // @todo: use flight SK249 from OSL-BGO, which operates monday-thursday, and sunday
    }

    @Test
    @Ignore
    public void convertFlightWithDifferentDepartureTimesInPattern() throws Exception {
    }

    @Test
    @Ignore
    public void doNotFindPossibleStopoversForFlight() throws Exception {
    }

    @Test
    @Ignore
    public void findPossibleStopoversForFlight() throws Exception {
        Flight currentFlight = createDummyFlight(
                1L,
                "SK",
                "4455",
                LocalDate.parse("2017-01-01"),
                "BGO",
                LocalTime.parse("09:00:00"),
                "OSL",
                LocalTime.parse("09:30:00")
        );
        Flight destinationFlight = createDummyFlight(
                2L,
                "SK",
                "4455",
                LocalDate.parse("2017-01-01"),
                "OSL",
                LocalTime.parse("10:00:00"),
                "TRD",
                LocalTime.parse("10:30:00")
        );
        List<Flight> flights = Arrays.asList(destinationFlight);
        HashMap<String, List<Flight>> flightsByDepartureAirport = Maps.newHashMap();
        flightsByDepartureAirport.put(destinationFlight.getDepartureStation(), flights);

        LinkedList<Flight> stopoversForFlight = clazzUnderTest.findPossibleStopoversForFlight(
                currentFlight, flightsByDepartureAirport, Lists.newLinkedList());

        Assertions.assertThat(stopoversForFlight)
                .isNotNull()
                .isNotEmpty()
                .hasSize(1);

    }

    @Test
    public void doNotFindPresentStopoverFlight() throws Exception {
        Flight currentFlight = createDummyFlight(
                1L,
                "SK",
                "4455",
                LocalDate.parse("2017-01-01"),
                "BGO",
                LocalTime.parse("09:00:00"),
                "OSL",
                LocalTime.parse("09:30:00")
        );
        Flight destinationFlight = createDummyFlight(
                11L,
                "WF",
                "8899",
                LocalDate.parse("2017-01-03"),
                "OSL",
                LocalTime.parse("08:00:00"),
                "TRD",
                LocalTime.parse("08:30:00")
        );
        List<Flight> flights = Arrays.asList(destinationFlight);

        Flight presentFlight = clazzUnderTest.findPresentStopoverFlight(currentFlight, flights);

        Assertions.assertThat(presentFlight)
                .isNull();
    }

    @Test
    public void findPresentStopoverFlight() throws Exception {
        Flight currentFlight = createDummyFlight(
                1L,
                "SK",
                "4455",
                LocalDate.parse("2017-01-01"),
                "BGO",
                LocalTime.parse("09:00:00"),
                "OSL",
                LocalTime.parse("09:30:00")
        );
        Flight destinationFlight = createDummyFlight(
                2L,
                "SK",
                "4455",
                LocalDate.parse("2017-01-01"),
                "OSL",
                LocalTime.parse("10:00:00"),
                "TRD",
                LocalTime.parse("10:30:00")
        );
        List<Flight> flights = Arrays.asList(destinationFlight);

        Flight presentFlight = clazzUnderTest.findPresentStopoverFlight(currentFlight, flights);

        Assertions.assertThat(presentFlight)
                .isNotNull()
                .isSameAs(destinationFlight);
    }

    @Test
    public void doNotMatchStopoverFlightPredicate() throws Exception {
        Flight flight1 = createDummyFlight(
                1L,
                "SK",
                "4455",
                LocalDate.parse("2017-01-01"),
                "BGO",
                LocalTime.parse("09:00:00"),
                "OSL",
                LocalTime.parse("09:30:00")
        );
        Flight flight2 = createDummyFlight(
                11L,
                "WF",
                "8899",
                LocalDate.parse("2017-01-03"),
                "OSL",
                LocalTime.parse("08:00:00"),
                "TRD",
                LocalTime.parse("08:30:00")
        );
        Predicate<Flight> predicate = clazzUnderTest.createStopoverFlightPredicate(flight1);

        Assertions.assertThat(predicate.test(flight2))
                .isFalse();
    }

    @Test
    public void matchStopoverFlightPredicate() throws Exception {
        Flight flight1 = createDummyFlight(
                1L,
                "SK",
                "4455",
                LocalDate.parse("2017-01-01"),
                "BGO",
                LocalTime.parse("09:00:00"),
                "OSL",
                LocalTime.parse("09:30:00")
        );
        Flight flight2 = createDummyFlight(
                2L,
                "SK",
                "4455",
                LocalDate.parse("2017-01-01"),
                "OSL",
                LocalTime.parse("10:00:00"),
                "TRD",
                LocalTime.parse("10:30:00")
        );
        Predicate<Flight> predicate = clazzUnderTest.createStopoverFlightPredicate(flight1);

        Assertions.assertThat(predicate.test(flight2))
                .isTrue();
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
    public void convertFlightToScheduledDirectFlight() throws Exception {
        Flight dummyFlight = createDummyFlight(
                1L,
                "SK",
                "4455",
                LocalDate.parse("2017-01-01"),
                "BGO",
                LocalTime.MIN,
                "OSL",
                LocalTime.MAX
        );
        ScheduledDirectFlight directFlight = clazzUnderTest.convertToScheduledDirectFlight(dummyFlight);

        Assertions.assertThat(directFlight)
                .isNotNull()
                .isInstanceOf(ScheduledDirectFlight.class);
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

    private List<Flight> createDummyFlights() {
        return Lists.newArrayList(
                createDummyFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO", LocalTime.MIN, "OSL", LocalTime.MAX),
                createDummyFlight(2L, "DY", "6677", LocalDate.parse("2017-01-02"), "BGO", LocalTime.MIN, "TRD", LocalTime.MAX),
                createDummyFlight(3L, "WF", "199", LocalDate.parse("2017-01-03"), "BGO", LocalTime.MIN, "SVG", LocalTime.MAX)
        );
    }

    private Flight createDummyFlight(long dummyId, String dummyDesignator, String dummyFlightNumber, LocalDate dummyDateOfOperation,
                                     String dummyDepartureStation, LocalTime dummyDepartureTime, String dummyArrivalStation, LocalTime dummyArrivalTime) {
        return new Flight() {{
            setId(BigInteger.valueOf(dummyId));
            setAirlineDesignator(dummyDesignator);
            setFlightNumber(dummyFlightNumber);
            setDateOfOperation(dummyDateOfOperation);
            setDepartureStation(dummyDepartureStation);
            setStd(dummyDepartureTime);
            setArrivalStation(dummyArrivalStation);
            setSta(dummyArrivalTime);
        }};
    }

    private <T> T generateObjectsFromXml(String resourceName, Class<T> clazz) throws JAXBException {
        return JAXBContext.newInstance(clazz).createUnmarshaller().unmarshal(
                new StreamSource(getClass().getResourceAsStream(resourceName)), clazz).getValue();
    }
}