package no.rutebanken.extime.util;

import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.ExtimeRouteBuilderIntegrationTestBase;
import no.rutebanken.extime.model.StopVisitType;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class AvinorTimetableUtilsTest  extends ExtimeRouteBuilderIntegrationTestBase {

    @Autowired
    private AvinorTimetableUtils avinorTimetableUtils;

    @Test
    public void testIsValidFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("OSL");
            setArrivalStation("BGO");
            setServiceType("J");
        }};

        boolean isValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.DEPARTURE, flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    public void testIsValidArrivalToOslFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("OSL");
            setArrivalStation("BGO");
            setServiceType("J");
        }};

        boolean isValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.ARRIVAL, flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    public void testIsNotValidArrivalToOslFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("BGO");
            setArrivalStation("OSL");
            setServiceType("J");
        }};

        boolean isValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.ARRIVAL, flight);

        Assertions.assertThat(isValidFlight).isFalse();
    }

    @Test
    public void testIsNotValidCharterFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("OSL");
            setArrivalStation("BGO");
            setServiceType("C");
        }};

        boolean isNotValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.DEPARTURE, flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    public void testIsNotValidInternationalFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("LHR");
            setArrivalStation("EWR");
            setServiceType("J");
        }};

        boolean isNotValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.DEPARTURE, flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    public void testIsScheduledPassengerFlight() throws Exception {
        Flight flight = new Flight() {{
            setServiceType("J");
        }};

        boolean isValidFlight = AvinorTimetableUtils.isScheduledPassengerFlight(flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    public void testIsNotScheduledPassengerFlight() throws Exception {
        Flight flight = new Flight() {{
            setServiceType("C");
        }};

        boolean isNotValidFlight = AvinorTimetableUtils.isScheduledPassengerFlight(flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    public void testIsValidDomesticFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("OSL");
            setArrivalStation("BGO");
        }};

        boolean isValidFlight = AvinorTimetableUtils.isDomesticFlight(flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    public void testIsNotValidDomesticFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("LHR");
            setArrivalStation("EWR");
        }};

        boolean isNotValidFlight = AvinorTimetableUtils.isDomesticFlight(flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    public void testIsValidDepartureAndArrival() throws Exception {
        boolean bothValid = AvinorTimetableUtils.isValidDepartureAndArrival("OSL", "BGO");
        Assertions.assertThat(bothValid).isTrue();
    }

    @Test
    public void testIsNotValidDepartureAndArrival() throws Exception {
        boolean bothInvalid = AvinorTimetableUtils.isValidDepartureAndArrival("AAA", "EWR");
        Assertions.assertThat(bothInvalid).isFalse();

        boolean firstInvalid = AvinorTimetableUtils.isValidDepartureAndArrival("AAA", "OSL");
        Assertions.assertThat(firstInvalid).isFalse();

        boolean lastInvalid = AvinorTimetableUtils.isValidDepartureAndArrival("OSL", "EWR");
        Assertions.assertThat(lastInvalid).isFalse();
    }

}