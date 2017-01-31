package no.rutebanken.extime.model;

import no.avinor.flydata.xjc.model.scheduled.Flight;
import org.junit.Test;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

public class FlightPredicateTest {

    @Test
    public void testFullMatchOFPreviousFlight() throws Exception {
        Flight flight1 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "HOV", OffsetTime.parse("08:30:00Z"));

        Flight flight2 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("07:00:00Z"), "BGO", OffsetTime.parse("07:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchPreviousFlight(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    public void testFullMatchOFNextFlight() throws Exception {
        Flight flight1 = createFlight(1L, "WF", "149", LocalDate.parse("2016-12-24"), "OSL",
                OffsetTime.parse("06:55:00Z"), "HOV", OffsetTime.parse("07:30:00Z"));

        Flight flight2 = createFlight(2L, "WF", "149", LocalDate.parse("2016-12-24"), "HOV",
                OffsetTime.parse("08:00:00Z"), "SOG", OffsetTime.parse("08:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchNextFlight(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    public void testMatchPreviousFlightId() throws Exception {
        Flight flight1 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchPreviousFlightId(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    public void testDoNotMatchPreviousFlightId() throws Exception {
        Flight flight1 = createFlight(10L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchPreviousFlightId(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    public void testMatchNextFlightId() throws Exception {
        Flight flight1 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchNextFlightId(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    public void testDoNotMatchNextFlightId() throws Exception {
        Flight flight1 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchNextFlightId(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    public void testMatchPreviousIata() throws Exception {
        Flight flight1 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "BGO", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchPreviousByIata(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    public void testDoNotMatchPreviousIata() throws Exception {
        Flight flight1 = createFlight(10L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "TOS",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchPreviousByIata(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    public void testMatchNextIata() throws Exception {
        Flight flight1 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "TRD",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchNextByIata(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    public void testDoNotMatchNextIata() throws Exception {
        Flight flight1 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "TRD", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchNextByIata(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    public void testMatchPreviousTime() throws Exception {
        Flight flight1 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("09:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("08:00:00Z"), "TRD", OffsetTime.parse("08:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchPreviousByTime(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    public void testDoNotMatchPreviousTime() throws Exception {
        Flight flight1 = createFlight(10L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "TOS",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchPreviousByTime(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    public void testMatchNextTime() throws Exception {
        Flight flight1 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "TRD",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchNextByTime(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    public void testDoNotMatchNextTime() throws Exception {
        Flight flight1 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("07:00:00Z"), "TRD", OffsetTime.parse("07:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchNextByTime(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    public void testMatchDesignator() throws Exception {
        Flight flight1 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchDesignator(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    public void testDoNotMatchDesignator() throws Exception {
        Flight flight1 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(2L, "DY", "8899", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchDesignator(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    public void testMatchFlightNumber() throws Exception {
        Flight flight1 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchFlightNumber(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    public void testDoNotMatchFlightNumber() throws Exception {
        Flight flight1 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(2L, "DY", "8899", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchFlightNumber(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    public void testMatchDateOfOperation() throws Exception {
        Flight flight1 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(2L, "SK", "4455", LocalDate.parse("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchDateOfOperation(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    public void testDoNotMatchDateOfOperation() throws Exception {
        Flight flight1 = createFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z"), "OSL", OffsetTime.parse("08:30:00Z"));
        Flight flight2 = createFlight(2L, "DY", "8899", LocalDate.parse("2018-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z"), "TRD", OffsetTime.parse("09:30:00Z"));

        Predicate<Flight> predicate = FlightPredicate.matchDateOfOperation(flight1);
        assertThat(predicate.test(flight2)).isFalse();
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

}