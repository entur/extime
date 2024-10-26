package no.rutebanken.extime.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Build HTTP request URLs to query the Avinor REST API.
 */
@Component
public class FlightRequestBuilder {

    private final String uri;
    private final Duration durationForward;
    private final Duration durationBack;

    /**
     *
     * @param uri URI of the Avinor REST API
     * @param durationForward time window to fetch flights back in time.
     * @param durationBack time window to fetch flights forward in time.
     */
    public FlightRequestBuilder(
            @Value("${avinor.timetable.feed.endpoint}") String uri,
            @Value("${avinor.timetable.period.forward:14d}") Duration durationForward,
            @Value("${avinor.timetable.period.back:2d}") Duration durationBack
            ) {
        this.uri = uri;
        this.durationForward = durationForward;
        this.durationBack = durationBack;
    }

    public List<FlightRequest> generateFlightRequests() {
        long numHourForward = durationForward.toHours();
        long numHourBack = durationBack.toHours();
        return Arrays.stream(AirportIATA.values()).map(airportIATA -> new FlightRequest(uri, airportIATA.name(), numHourBack, numHourForward)).toList();

    }
}
