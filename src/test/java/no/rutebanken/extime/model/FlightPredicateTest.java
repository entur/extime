package no.rutebanken.extime.model;

import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.util.DateUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class FlightPredicateTest {

    @Test
    void testFullMatchOFPreviousFlight() {
        Flight flight1 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "HOV", OffsetTime.parse("08:30:00Z").toLocalTime());

        Flight flight2 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("07:00:00Z").toLocalTime(), "BGO", OffsetTime.parse("07:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchPreviousFlight(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    void testFullMatchOFNextFlight() {
        Flight flight1 = createFlight(1L, "WF", "149", DateUtils.parseDate("2016-12-24"), "OSL",
                OffsetTime.parse("06:55:00Z").toLocalTime(), "HOV", OffsetTime.parse("07:30:00Z").toLocalTime());

        Flight flight2 = createFlight(2L, "WF", "149", DateUtils.parseDate("2016-12-24"), "HOV",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "SOG", OffsetTime.parse("08:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchNextFlight(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    void testMatchPreviousFlightId() {
        Flight flight1 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchPreviousFlightId(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    void testDoNotMatchPreviousFlightId() {
        Flight flight1 = createFlight(10L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchPreviousFlightId(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    void testMatchNextFlightId() {
        Flight flight1 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchNextFlightId(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    void testDoNotMatchNextFlightId() {
        Flight flight1 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchNextFlightId(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    void testMatchPreviousIata() {
        Flight flight1 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "BGO", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchPreviousByIata(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    void testDoNotMatchPreviousIata() {
        Flight flight1 = createFlight(10L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "TOS",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchPreviousByIata(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    void testMatchNextIata() {
        Flight flight1 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "TRD",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchNextByIata(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    void testDoNotMatchNextIata() {
        Flight flight1 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchNextByIata(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    void testMatchPreviousTime() {
        Flight flight1 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("08:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchPreviousByTime(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    void testDoNotMatchPreviousTime() {
        Flight flight1 = createFlight(10L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "TOS",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchPreviousByTime(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    void testMatchNextTime() {
        Flight flight1 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "TRD",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchNextByTime(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    void testDoNotMatchNextTime() {
        Flight flight1 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("07:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("07:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchNextByTime(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    void testMatchDesignator() {
        Flight flight1 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchDesignator(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    void testDoNotMatchDesignator() {
        Flight flight1 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(2L, "DY", "8899", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchDesignator(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    void testMatchFlightNumber() {
        Flight flight1 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchFlightNumber(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    void testDoNotMatchFlightNumber() {
        Flight flight1 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(2L, "DY", "8899", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchFlightNumber(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    @Test
    void testMatchDateOfOperation() {
        Flight flight1 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(2L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchDateOfOperation(flight1);
        assertThat(predicate.test(flight2)).isTrue();
    }

    @Test
    void testDoNotMatchDateOfOperation() {
        Flight flight1 = createFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01Z"), "BGO",
                OffsetTime.parse("08:00:00Z").toLocalTime(), "OSL", OffsetTime.parse("08:30:00Z").toLocalTime());
        Flight flight2 = createFlight(2L, "DY", "8899", DateUtils.parseDate("2018-01-01"), "OSL",
                OffsetTime.parse("09:00:00Z").toLocalTime(), "TRD", OffsetTime.parse("09:30:00Z").toLocalTime());

        Predicate<Flight> predicate = FlightPredicate.matchDateOfOperation(flight1);
        assertThat(predicate.test(flight2)).isFalse();
    }

    private Flight createFlight(long id, String designator, String flightNumber, ZonedDateTime dateOfOperation,
            String departureStation, LocalTime departureTime, String arrivalStation, LocalTime arrivalTime) {
        Flight flight = new Flight();
        flight.setId(id);
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