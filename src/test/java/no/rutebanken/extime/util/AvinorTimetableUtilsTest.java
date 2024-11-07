package no.rutebanken.extime.util;

import no.rutebanken.extime.model.FlightLeg;
import no.rutebanken.extime.ExtimeRouteBuilderIntegrationTestBase;
import no.rutebanken.extime.model.FlightLegBuilder;
import no.rutebanken.extime.model.StopVisitType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class AvinorTimetableUtilsTest  extends ExtimeRouteBuilderIntegrationTestBase {

    @Test
    void testIsValidFlight() {
        FlightLeg flight = new FlightLegBuilder()
                .withDepartureAirport("OSL")
                .withArrivalAirport("BGO")
                .build();

        boolean isValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.DEPARTURE, flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    void testIsValidArrivalToOslFlight() {
        FlightLeg flight = new FlightLegBuilder()
                .withDepartureAirport("OSL")
                .withArrivalAirport("BGO")
                .build();

        boolean isValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.ARRIVAL, flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    void testIsNotValidArrivalToOslFlight()  {
        FlightLeg flight = new FlightLegBuilder()
                .withDepartureAirport("BGO")
                .withArrivalAirport("OSL")
                .build();

        boolean isValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.ARRIVAL, flight);

        Assertions.assertThat(isValidFlight).isFalse();
    }

    @Test
    void testIsNotValidInternationalFlight()  {
        FlightLeg flight = new FlightLegBuilder()
                .withDepartureAirport("LHR")
                .withArrivalAirport("EWR")
                .build();

        boolean isNotValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.DEPARTURE, flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    void testIsValidDomesticFlight()  {
        FlightLeg flight = new FlightLegBuilder()
                .withDepartureAirport("OSL")
                .withArrivalAirport("BGO")
                .build();

        boolean isValidFlight = AvinorTimetableUtils.isDomesticFlight(flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    void testIsNotValidDomesticFlight()  {
        FlightLeg flight = new FlightLegBuilder()
                .withDepartureAirport("LHR")
                .withArrivalAirport("EWR")
                .build();

        boolean isNotValidFlight = AvinorTimetableUtils.isDomesticFlight(flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    void testIsValidDepartureAndArrival()  {
        boolean bothValid = AvinorTimetableUtils.isValidDepartureAndArrival("OSL", "BGO");
        Assertions.assertThat(bothValid).isTrue();
    }

    @Test
    void testIsNotValidDepartureAndArrival()  {
        boolean bothInvalid = AvinorTimetableUtils.isValidDepartureAndArrival("AAA", "EWR");
        Assertions.assertThat(bothInvalid).isFalse();

        boolean firstInvalid = AvinorTimetableUtils.isValidDepartureAndArrival("AAA", "OSL");
        Assertions.assertThat(firstInvalid).isFalse();

        boolean lastInvalid = AvinorTimetableUtils.isValidDepartureAndArrival("OSL", "EWR");
        Assertions.assertThat(lastInvalid).isFalse();
    }

}