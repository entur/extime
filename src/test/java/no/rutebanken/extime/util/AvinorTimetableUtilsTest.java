package no.rutebanken.extime.util;

import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.ExtimeRouteBuilderIntegrationTestBase;
import no.rutebanken.extime.model.StopVisitType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class AvinorTimetableUtilsTest  extends ExtimeRouteBuilderIntegrationTestBase {

    @Test
    public void testIsValidFlight() {
        Flight flight = new Flight();
        flight.setDepartureStation("OSL");
        flight.setArrivalStation("BGO");
        flight.setServiceType("J");

        boolean isValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.DEPARTURE, flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    public void testIsValidArrivalToOslFlight() {
        Flight flight = new Flight();
        flight.setDepartureStation("OSL");
        flight.setArrivalStation("BGO");
        flight.setServiceType("J");

        boolean isValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.ARRIVAL, flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    public void testIsNotValidArrivalToOslFlight()  {
        Flight flight = new Flight();
        flight.setDepartureStation("BGO");
        flight.setArrivalStation("OSL");
        flight.setServiceType("J");

        boolean isValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.ARRIVAL, flight);

        Assertions.assertThat(isValidFlight).isFalse();
    }

    @Test
    public void testIsNotValidCharterFlight()  {
        Flight flight = new Flight();
        flight.setDepartureStation("OSL");
        flight.setArrivalStation("BGO");
        flight.setServiceType("C");

        boolean isNotValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.DEPARTURE, flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    public void testIsNotValidInternationalFlight()  {
        Flight flight = new Flight();
        flight.setDepartureStation("LHR");
        flight.setArrivalStation("EWR");
        flight.setServiceType("J");

        boolean isNotValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.DEPARTURE, flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    public void testIsScheduledPassengerFlight()  {
        Flight flight = new Flight();
        flight.setServiceType("J");

        boolean isValidFlight = AvinorTimetableUtils.isScheduledPassengerFlight(flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    public void testIsNotScheduledPassengerFlight()  {
        Flight flight = new Flight();
        flight.setServiceType("C");

        boolean isNotValidFlight = AvinorTimetableUtils.isScheduledPassengerFlight(flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    public void testIsValidDomesticFlight()  {
        Flight flight = new Flight();
        flight.setDepartureStation("OSL");
        flight.setArrivalStation("BGO");

        boolean isValidFlight = AvinorTimetableUtils.isDomesticFlight(flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    public void testIsNotValidDomesticFlight()  {
        Flight flight = new Flight();
        flight.setDepartureStation("LHR");
        flight.setArrivalStation("EWR");

        boolean isNotValidFlight = AvinorTimetableUtils.isDomesticFlight(flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    public void testIsValidDepartureAndArrival()  {
        boolean bothValid = AvinorTimetableUtils.isValidDepartureAndArrival("OSL", "BGO");
        Assertions.assertThat(bothValid).isTrue();
    }

    @Test
    public void testIsNotValidDepartureAndArrival()  {
        boolean bothInvalid = AvinorTimetableUtils.isValidDepartureAndArrival("AAA", "EWR");
        Assertions.assertThat(bothInvalid).isFalse();

        boolean firstInvalid = AvinorTimetableUtils.isValidDepartureAndArrival("AAA", "OSL");
        Assertions.assertThat(firstInvalid).isFalse();

        boolean lastInvalid = AvinorTimetableUtils.isValidDepartureAndArrival("OSL", "EWR");
        Assertions.assertThat(lastInvalid).isFalse();
    }

}