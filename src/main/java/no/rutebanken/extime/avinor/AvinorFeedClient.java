package no.rutebanken.extime.avinor;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import no.rutebanken.extime.model.FlightEvent;
import no.rutebanken.extime.model.FlightEventMapper;
import no.rutebanken.extime.model.FlightRequest;
import no.rutebanken.extime.util.ExtimeException;
import no.rutebanken.extime.util.Retry;
import no.rutebanken.extime.util.RetrySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fetches one airport's timetable from Avinor's XML feed.
 *
 * <p>Replaces the {@code direct:fetchTimetableForAirport} route. Three pieces of what that route did
 * came from Camel rather than from any line of extime's code, and are reproduced here explicitly:
 *
 * <ul>
 *   <li>the {@code throttle(1).timePeriodMillis(100)} rate limit, which bounds the whole service to ten
 *       requests per second against Avinor no matter how many threads are fetching;</li>
 *   <li>the route-level {@code errorHandler}, now {@link Retry};</li>
 *   <li>refusing to follow redirects, which is camel-http's default and the JDK client's;</li>
 *   <li>a connect timeout. Note that {@link HttpRequest.Builder#timeout} bounds the connect and the wait
 *       for response headers, and <em>not</em> the body read; camel-http bounded that too, through Apache
 *       HttpClient's three-minute {@code SO_TIMEOUT}. A peer that sends headers and then stalls would
 *       block a fetch thread forever, so the deadline that replaces it lives in
 *       {@link FlightEventFetcher}, where it can bound the whole fan-out at once.</li>
 * </ul>
 *
 * <p>The response is parsed straight off the wire, so the encoding comes from the XML declaration
 * (Avinor sends {@code iso-8859-1}). Camel instead decoded using the charset from the HTTP
 * {@code Content-Type} header. The two agree on this feed, and parsing by declaration is what the
 * offline dump path has always done.
 */
@Component
public class AvinorFeedClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AvinorFeedClient.class);

    private final HttpClient httpClient;
    private final JAXBContext flightsContext;
    private final FlightEventMapper flightEventMapper = new FlightEventMapper();
    private final RetrySettings retrySettings;
    private final Duration requestTimeout;
    private final long minRequestIntervalNanos;
    private final boolean dumpOutput;
    private final Path dumpOutputPath;

    private final Object throttleLock = new Object();
    private long nextRequestAtNanos = System.nanoTime();

    public AvinorFeedClient(
            RetrySettings retrySettings,
            @Value("${extime.timetable.fetch.connect.timeout:10s}") Duration connectTimeout,
            @Value("${extime.timetable.fetch.request.timeout:120s}") Duration requestTimeout,
            @Value("${extime.timetable.fetch.min.interval:100ms}") Duration minRequestInterval,
            @Value("${avinor.timetable.dump.output:false}") boolean dumpOutput,
            @Value("${avinor.timetable.dump.output.path:}") String dumpOutputPath) {
        this.retrySettings = retrySettings;
        this.requestTimeout = requestTimeout;
        this.minRequestIntervalNanos = minRequestInterval.toNanos();
        this.dumpOutput = dumpOutput;
        this.dumpOutputPath = dumpOutputPath.isBlank() ? null : Path.of(dumpOutputPath);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        try {
            this.flightsContext = JAXBContext.newInstance(Flights.class.getPackage().getName());
        } catch (JAXBException e) {
            throw new ExtimeException("Could not create the JAXB context for the Avinor feed", e);
        }
    }

    /**
     * Fetch and map one airport's flights, retrying transient failures.
     */
    public List<FlightEvent> fetchFlightEvents(FlightRequest flightRequest) {
        return Retry.withRetry(retrySettings,
                "Fetching flights for " + flightRequest.airportName(),
                () -> doFetch(flightRequest));
    }

    private List<FlightEvent> doFetch(FlightRequest flightRequest) throws IOException, InterruptedException {
        throttle();
        LOGGER.debug("Sending request {}", flightRequest.request());
        HttpRequest request = HttpRequest.newBuilder(URI.create(flightRequest.request()))
                .timeout(requestTimeout)
                .GET()
                .build();
        HttpResponse<InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Avinor returned HTTP %d for %s"
                        .formatted(response.statusCode(), flightRequest.request()));
            }
            List<FlightEvent> flightEvents = flightEventMapper.mapToFlightEvent(read(flightRequest, body));
            LOGGER.debug("Retrieved {} flight events for {}", flightEvents.size(), flightRequest.airportName());
            return flightEvents;
        }
    }

    private Flights read(FlightRequest flightRequest, InputStream body) throws IOException {
        if (!dumpOutput) {
            return unmarshal(body);
        }
        // Camel wrote the raw response to disk with the file component before unmarshalling it. Read it
        // back rather than buffering, so that a large response is not held in memory twice.
        Path target = dumpFile(flightRequest.airportName());
        Files.createDirectories(target.getParent());
        Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
        LOGGER.info("Dumped the Avinor response for {} to {}", flightRequest.airportName(), target);
        try (InputStream dumped = Files.newInputStream(target)) {
            return unmarshal(dumped);
        }
    }

    private Path dumpFile(String airportName) {
        if (dumpOutputPath == null) {
            throw new ExtimeException(
                    "avinor.timetable.dump.output is enabled but avinor.timetable.dump.output.path is not set");
        }
        return dumpOutputPath.resolve(airportName + ".xml");
    }

    /**
     * Unmarshals by root element rather than by declared type, so anything that is not the feed is an
     * error. The declared-type overload binds whatever root it finds onto {@link Flights} and returns
     * zero flights, which the export cannot tell apart from an airport with no departures. Camel's
     * {@code JaxbDataFormat} was root-element driven for the same reason.
     */
    private Flights unmarshal(InputStream in) {
        try {
            Object root = flightsContext.createUnmarshaller().unmarshal(new StreamSource(in));
            if (root instanceof JAXBElement<?> element) {
                root = element.getValue();
            }
            if (root instanceof Flights flights) {
                return flights;
            }
            throw new ExtimeException("The Avinor feed returned a " + root.getClass().getName() + ", not flights");
        } catch (JAXBException e) {
            throw new ExtimeException("Error while unmarshalling the Avinor feed", e);
        }
    }

    /**
     * Keeps at least {@code extime.timetable.fetch.min.interval} between the start of any two requests,
     * across all fetch threads. This is the replacement for Camel's throttle EIP, which was equally
     * global: the throttle sat on a shared route, not on the parallel split.
     */
    private void throttle() throws InterruptedException {
        long waitNanos;
        synchronized (throttleLock) {
            long now = System.nanoTime();
            waitNanos = Math.max(0, nextRequestAtNanos - now);
            nextRequestAtNanos = Math.max(now, nextRequestAtNanos) + minRequestIntervalNanos;
        }
        if (waitNanos > 0) {
            TimeUnit.NANOSECONDS.sleep(waitNanos);
        }
    }
}
