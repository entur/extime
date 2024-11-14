package no.rutebanken.extime.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlightRequestBuilderTest {

    public static final String URI = "https://asrv.avinor.no/XmlFeedScheduled/v1.0";
    private static final LocalDate TODAY = LocalDate.of(2024,11,14);
    public static final Duration DURATION_FORWARD = Duration.ofDays(1);
    public static final Duration DURATION_BACK = Duration.ofDays(2);

    @Test
    void test() {
        FlightRequestBuilder builder = new FlightRequestBuilder(URI, DURATION_FORWARD, DURATION_BACK);
        List<FlightRequest> flightRequests = builder.generateFlightRequestsForDay(TODAY);
        assertNotNull(flightRequests);
        assertEquals(AirportIATA.values().length, flightRequests.size());
        FlightRequest flightRequestOslo = flightRequests.stream().filter(flightRequest -> flightRequest.airportName().equals(AirportIATA.OSL.name())).findFirst().orElseThrow();
        assertEquals(URI + "?airport=OSL&PeriodFrom=2024-11-12&PeriodTo=2024-11-15&direction=D", flightRequestOslo.request());

    }

}