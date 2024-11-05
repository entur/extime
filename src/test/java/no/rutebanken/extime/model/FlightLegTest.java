package no.rutebanken.extime.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static no.rutebanken.extime.TestUtils.createFlight;

class FlightLegTest {

    @Test
    void testIsNextLeg() {
        FlightLeg flight1 = createFlight(1L, "WF", "149", "OSL",
                ZonedDateTime.parse("2017-01-01T06:55:00Z"), "HOV", ZonedDateTime.parse("2017-01-01T07:30:00Z"));

        FlightLeg flight2 = createFlight(2L, "WF", "149", "HOV",
                ZonedDateTime.parse("2017-01-01T08:00:00Z"), "SOG", ZonedDateTime.parse("2017-01-01T08:30:00Z"));

        Assertions.assertTrue(flight2.isNextLegOf(flight1));
    }

    @Test
    void testMatchNextIata() {
        FlightLeg flight1 = createFlight(1L, "SK", "4455", "TRD",
                ZonedDateTime.parse("2017-01-01T08:00:00Z"), "OSL", ZonedDateTime.parse("2017-01-01T08:30:00Z"));
        FlightLeg flight2 = createFlight(2L, "SK", "4455", "OSL",
                ZonedDateTime.parse("2017-01-01T09:00:00Z"), "TRD", ZonedDateTime.parse("2017-01-01T09:30:00Z"));

        Assertions.assertTrue(flight1.departFromArrivalAirportOf(flight2));

    }

    @Test
    void testDoNotMatchNextIata() {
        FlightLeg flight1 = createFlight(2L, "SK", "4455", "BGO",
                ZonedDateTime.parse("2017-01-01T08:00:00Z"), "TRD", ZonedDateTime.parse("2017-01-01T08:30:00Z"));
        FlightLeg flight2 = createFlight(1L, "SK", "4455", "OSL",
                ZonedDateTime.parse("2017-01-01T09:00:00Z"), "TRD", ZonedDateTime.parse("2017-01-01T09:30:00Z"));

        Assertions.assertFalse(flight1.departFromArrivalAirportOf(flight2));
    }

    @Test
    void testMatchDesignator() {
        FlightLeg flight1 = createFlight(1L, "SK", "4455", "BGO",
                ZonedDateTime.parse("2017-01-01T08:00:00Z"), "OSL", ZonedDateTime.parse("2017-01-01T08:30:00Z"));
        FlightLeg flight2 = createFlight(2L, "SK", "4455", "OSL",
                ZonedDateTime.parse("2017-01-01T09:00:00Z"), "TRD", ZonedDateTime.parse("2017-01-01T09:30:00Z"));

        Assertions.assertTrue(flight1.hasSameAirlineAs(flight2));

    }

    @Test
    void testDoNotMatchDesignator() {
        FlightLeg flight1 = createFlight(1L, "SK", "4455", "BGO",
                ZonedDateTime.parse("2017-01-01T08:00:00Z"), "OSL", ZonedDateTime.parse("2017-01-01T08:30:00Z"));
        FlightLeg flight2 = createFlight(2L, "DY", "8899", "OSL",
                ZonedDateTime.parse("2017-01-01T09:00:00Z"), "TRD", ZonedDateTime.parse("2017-01-01T09:30:00Z"));

        Assertions.assertFalse(flight1.hasSameAirlineAs(flight2));

    }

    @Test
    void testMatchFlightNumber() {
        FlightLeg flight1 = createFlight(1L, "SK", "4455", "BGO",
                ZonedDateTime.parse("2017-01-01T08:00:00Z"), "OSL", ZonedDateTime.parse("2017-01-01T08:30:00Z"));
        FlightLeg flight2 = createFlight(2L, "SK", "4455", "OSL",
                ZonedDateTime.parse("2017-01-01T09:00:00Z"), "TRD", ZonedDateTime.parse("2017-01-01T09:30:00Z"));

        Assertions.assertTrue(flight1.hasSameFlightNumberAs(flight2));

    }

    @Test
    void testDoNotMatchFlightNumber() {
        FlightLeg flight1 = createFlight(1L, "SK", "4455", "BGO",
                ZonedDateTime.parse("2017-01-01T08:00:00Z"), "OSL", ZonedDateTime.parse("2017-01-01T08:30:00Z"));
        FlightLeg flight2 = createFlight(2L, "DY", "8899", "OSL",
                ZonedDateTime.parse("2017-01-01T09:00:00Z"), "TRD", ZonedDateTime.parse("2017-01-01T09:30:00Z"));

        Assertions.assertFalse(flight1.hasSameFlightNumberAs(flight2));

    }

    @Test
    void testMatchMaxLayoverDuration() {
        FlightLeg flight1 = createFlight(1L, "SK", "4455", "BGO",
                ZonedDateTime.parse("2017-01-01T08:00:00Z"), "OSL", ZonedDateTime.parse("2017-01-01T08:30:00Z"));
        FlightLeg flight2 = createFlight(2L, "SK", "4455", "OSL",
                ZonedDateTime.parse("2017-01-01T09:00:00Z"), "TRD", ZonedDateTime.parse("2017-01-01T09:30:00Z"));

        Assertions.assertTrue(flight2.departSoonAfterArrivalOf(flight1));
    }

    @Test
    void testDoNotMatchMaxLayoverDuration() {
        FlightLeg flight1 = createFlight(1L, "SK", "4455", "BGO",
                ZonedDateTime.parse("2017-01-01T08:00:00Z"), "OSL", ZonedDateTime.parse("2017-01-01T08:30:00Z"));
        FlightLeg flight2 = createFlight(2L, "DY", "8899", "OSL",
                ZonedDateTime.parse("2018-01-01T09:00:00Z"), "TRD", ZonedDateTime.parse("2018-01-01T09:30:00Z"));

        Assertions.assertFalse(flight1.departSoonAfterArrivalOf(flight2));
    }



}