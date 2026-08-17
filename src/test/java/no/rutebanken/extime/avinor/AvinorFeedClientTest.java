package no.rutebanken.extime.avinor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import no.rutebanken.extime.model.FlightRequest;
import no.rutebanken.extime.util.ExtimeException;
import no.rutebanken.extime.util.RetrySettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serves the feed from an in-JVM HTTP server, so the path that talks to Avinor is exercised without the
 * network. The important case is the third one: a well-formed response that is not the feed must fail
 * rather than parse as an airport with no departures, because marduk replaces the previous dataset with
 * whatever extime uploads.
 */
class AvinorFeedClientTest {

    private HttpServer server;
    private final AtomicReference<Response> response = new AtomicReference<>();

    private record Response(int status, byte[] body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::respond);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void mapsTheFeedToFlightEvents() throws IOException {
        respondWith(200, Files.readAllBytes(Path.of("src/test/resources/testdata/SOG.xml")));

        assertThat(client().fetchFlightEvents(request())).isNotEmpty();
    }

    @Test
    void failsOnAnErrorStatus() {
        respondWith(503, "Service Unavailable".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> client().fetchFlightEvents(request()))
                .isInstanceOf(ExtimeException.class)
                .hasMessageContaining("SOG");
    }

    @Test
    void failsOnAWellFormedResponseThatIsNotTheFeed() {
        respondWith(200, "<html><body>Service unavailable</body></html>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> client().fetchFlightEvents(request()))
                .isInstanceOf(ExtimeException.class);
    }

    private void respondWith(int status, byte[] body) {
        response.set(new Response(status, body));
    }

    private void respond(HttpExchange exchange) throws IOException {
        Response toSend = response.get();
        exchange.sendResponseHeaders(toSend.status(), toSend.body().length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(toSend.body());
        }
    }

    private AvinorFeedClient client() {
        return new AvinorFeedClient(
                new RetrySettings(0, Duration.ofMillis(1), 1),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                Duration.ZERO,
                false,
                "");
    }

    private FlightRequest request() {
        String uri = "http://localhost:" + server.getAddress().getPort() + "/feed";
        return new FlightRequest(uri, "SOG", LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 28));
    }
}
