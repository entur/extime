package no.rutebanken.extime.avinor;

import no.rutebanken.extime.model.FlightEvent;
import no.rutebanken.extime.model.FlightRequest;
import no.rutebanken.extime.model.FlightRequestBuilder;
import no.rutebanken.extime.util.AvinorTimetableUtils;
import no.rutebanken.extime.util.ExtimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fetches every whitelisted airport's timetable and returns the flight events as one list.
 *
 * <p>Replaces {@code direct:fetchFlights}, which was a Camel split with {@code parallelProcessing} over
 * the airport requests. The concurrency came from Camel's default thread pool profile, sized by
 * {@code camel.threadpool.pool-size=4} in the deployed ConfigMap; that is now
 * {@code extime.timetable.fetch.threads}, with the same default.
 *
 * <p><strong>Failure handling differs deliberately.</strong> Camel's splitter aggregated with a strategy
 * that always returned the accumulator, so an exception on a sub-exchange was discarded unless it
 * happened to be the first one to complete: a failing airport usually produced a NeTEx dataset silently
 * missing that airport's flights, and occasionally failed the whole run, depending on completion order.
 * Since marduk replaces the previous dataset with whatever arrives, a partial export silently removes
 * real flights from the journey planner, whereas a failed run leaves the previous export in place. So
 * any airport that still fails after its retries fails the export.
 */
@Component
public class FlightEventFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlightEventFetcher.class);

    /**
     * How long the whole fan-out may take. A safety net rather than a tuning knob: a normal run is
     * minutes, and the only thing this catches is a request that will never finish on its own.
     */
    private static final Duration FETCH_DEADLINE = Duration.ofMinutes(30);

    private final FlightRequestBuilder flightRequestBuilder;
    private final AvinorFeedClient avinorFeedClient;
    private final AvinorTimetableUtils avinorTimetableUtils;
    private final int threads;
    private final boolean dumpInput;
    private final String dumpInputPath;

    public FlightEventFetcher(
            FlightRequestBuilder flightRequestBuilder,
            AvinorFeedClient avinorFeedClient,
            AvinorTimetableUtils avinorTimetableUtils,
            @Value("${extime.timetable.fetch.threads:4}") int threads,
            @Value("${avinor.timetable.dump.input:false}") boolean dumpInput,
            @Value("${avinor.timetable.dump.input.path:}") String dumpInputPath) {
        this.flightRequestBuilder = flightRequestBuilder;
        this.avinorFeedClient = avinorFeedClient;
        this.avinorTimetableUtils = avinorTimetableUtils;
        this.threads = threads;
        this.dumpInput = dumpInput;
        this.dumpInputPath = dumpInputPath;
    }

    public List<FlightEvent> fetchFlightEvents() {
        return dumpInput ? fetchFromDump() : fetchFromFeed();
    }

    private List<FlightEvent> fetchFromDump() {
        LOGGER.info("Fetching data from dump");
        try {
            return avinorTimetableUtils.generateFlightEventsFromFeedDump(dumpInputPath);
        } catch (IOException e) {
            throw new ExtimeException("Error while reading the Avinor feed dump from " + dumpInputPath, e);
        }
    }

    private List<FlightEvent> fetchFromFeed() {
        LOGGER.info("Fetching data from feed");
        List<FlightRequest> flightRequests = flightRequestBuilder.generateFlightRequests();

        Map<FlightRequest, Future<List<FlightEvent>>> pending = new LinkedHashMap<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (FlightRequest flightRequest : flightRequests) {
                pending.put(flightRequest, executor.submit(() -> {
                    LOGGER.info("Fetching flights for {} and date range : [{} , {}]",
                            flightRequest.airportName(), flightRequest.fromDate(), flightRequest.toDate());
                    return avinorFeedClient.fetchFlightEvents(flightRequest);
                }));
            }
            return collect(pending, Instant.now().plus(FETCH_DEADLINE));
        }
    }

    /**
     * Waits for every request before failing, so that one broken airport does not leave the others
     * running against Avinor after the export has already given up, and so that the log names all of
     * them rather than whichever failed first.
     *
     * <p>The deadline is what stops a stalled response body from wedging the service. Nothing else
     * bounds one: {@code HttpRequest.timeout} covers the wait for headers only, so a peer that sends
     * headers and then goes quiet blocks its fetch thread forever, and with it this loop, the
     * {@code ExecutorService.close()} above, and every export after it. Camel had the same exposure
     * covered by Apache HttpClient's three-minute socket timeout.
     */
    // Package-private so that the deadline can be tested without making it a production knob.
    List<FlightEvent> collect(Map<FlightRequest, Future<List<FlightEvent>>> pending, Instant deadline) {
        List<FlightEvent> flightEvents = new ArrayList<>();
        List<String> failedAirports = new ArrayList<>();

        for (Map.Entry<FlightRequest, Future<List<FlightEvent>>> entry : pending.entrySet()) {
            String airportName = entry.getKey().airportName();
            Future<List<FlightEvent>> pendingFlightEvents = entry.getValue();
            try {
                flightEvents.addAll(pendingFlightEvents.get(millisUntil(deadline), TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExtimeException("Interrupted while fetching flights for " + airportName, e);
            } catch (ExecutionException e) {
                LOGGER.error("Could not fetch flights for {}", airportName, e.getCause());
                failedAirports.add(airportName);
            } catch (TimeoutException e) {
                // Interrupting releases a thread blocked reading a stalled response body.
                pendingFlightEvents.cancel(true);
                LOGGER.error("Gave up fetching flights for {} after {}", airportName, FETCH_DEADLINE);
                failedAirports.add(airportName);
            }
        }

        if (!failedAirports.isEmpty()) {
            throw new ExtimeException("Aborting the export, could not fetch flights for " + failedAirports);
        }

        LOGGER.info("Retrieved {} flight events from {} airports", flightEvents.size(), pending.size());
        return flightEvents;
    }

    private static long millisUntil(Instant deadline) {
        return Math.max(0, Duration.between(Instant.now(), deadline).toMillis());
    }
}
