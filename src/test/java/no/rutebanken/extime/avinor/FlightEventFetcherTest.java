package no.rutebanken.extime.avinor;

import no.rutebanken.extime.model.AirlineIATA;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.FlightEvent;
import no.rutebanken.extime.model.FlightRequest;
import no.rutebanken.extime.model.FlightRequestBuilder;
import no.rutebanken.extime.util.ExtimeException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static no.rutebanken.extime.Constants.DEFAULT_ZONE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the one behaviour this migration deliberately changed. Camel's splitter discarded a failing
 * airport's exception unless it happened to complete first, so a broken airport usually produced a NeTEx
 * dataset silently missing its flights; marduk replaces the previous dataset with whatever arrives.
 */
class FlightEventFetcherTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 14);
    private static final LocalDate TO = FROM.plusDays(14);

    @Test
    void oneFailingAirportFailsTheExport() {
        FlightEventFetcher fetcher = fetcherFor(request("OSL"), request("BGO"));

        assertThatThrownBy(fetcher::fetchFlightEvents)
                .isInstanceOf(ExtimeException.class)
                .hasMessageContaining("BGO");
    }

    @Test
    void everyAirportsFlightsAreReturnedTogether() {
        FlightEventFetcher fetcher = fetcherFor(request("OSL"), request("TRD"));

        assertThat(fetcher.fetchFlightEvents()).hasSize(2);
    }

    /** BGO is the airport the stubbed feed refuses to serve. */
    private static FlightEventFetcher fetcherFor(FlightRequest... requests) {
        FlightRequestBuilder requestBuilder = mock(FlightRequestBuilder.class);
        when(requestBuilder.generateFlightRequests()).thenReturn(List.of(requests));

        AvinorFeedClient feedClient = mock(AvinorFeedClient.class);
        when(feedClient.fetchFlightEvents(any())).thenAnswer(invocation -> {
            FlightRequest request = invocation.getArgument(0);
            if ("BGO".equals(request.airportName())) {
                throw new ExtimeException("Avinor returned HTTP 503 for " + request.airportName());
            }
            return List.of(flightEventFrom(request.airportName()));
        });

        return new FlightEventFetcher(requestBuilder, feedClient, null, 2, false, "");
    }

    private static FlightRequest request(String airportName) {
        return new FlightRequest("https://feed.invalid", airportName, FROM, TO);
    }

    private static FlightEvent flightEventFrom(String airportName) {
        return new FlightEvent(1L, "DY1", AirlineIATA.DY, AirportIATA.valueOf(airportName), AirportIATA.SVG,
                ZonedDateTime.of(FROM, LocalTime.MIDNIGHT, ZoneId.of(DEFAULT_ZONE_ID)),
                LocalTime.of(10, 0), LocalTime.of(11, 0));
    }
}
