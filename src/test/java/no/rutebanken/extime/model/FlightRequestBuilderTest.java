package no.rutebanken.extime.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlightRequestBuilderTest {

    public static final String URI = "https://asrv.avinor.no/XmlFeed/v1.0";

    @Test
    void test() {
        FlightRequestBuilder builder = new FlightRequestBuilder(URI, Duration.ofDays(1), Duration.ofDays(2));
        List<FlightRequest> flightRequests = builder.generateFlightRequests();
        assertNotNull(flightRequests);
        assertEquals(AirportIATA.values().length, flightRequests.size());
        FlightRequest flightRequestOslo = flightRequests.stream().filter(flightRequest -> flightRequest.airportName().equals(AirportIATA.OSL.name())).findFirst().orElseThrow();
        assertEquals(URI + "?airport=OSL&TimeFrom=48&TimeTo=24&serviceType=J", flightRequestOslo.request());

    }

}