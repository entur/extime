/*
 *
 *  * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 *  * the European Commission - subsequent versions of the EUPL (the "Licence");
 *  * You may not use this work except in compliance with the Licence.
 *  * You may obtain a copy of the Licence at:
 *  *
 *  *   https://joinup.ec.europa.eu/software/page/eupl
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the Licence is distributed on an "AS IS" basis,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the Licence for the specific language governing permissions and
 *  * limitations under the Licence.
 *  *
 *
 */

package no.rutebanken.extime.actuator;

import no.rutebanken.extime.App;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The main purpose of this test is to check that the HTTP server is up and running.
 */
@SpringBootTest(classes = App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({ "default", "in-memory-blobstore" })
class ActuatorHealthEndpointIntegrationTest {

    @DynamicPropertySource
    static void actuatorProperties(DynamicPropertyRegistry registry) {
        registry.add("management.endpoints.web.exposure.include", () -> "health");
        registry.add("management.endpoints.web.exposure.exclude", () -> "");
        registry.add("management.endpoint.health.enabled", () -> "true");
        registry.add("management.endpoint.health.show-details", () -> "always");
    }

    @LocalServerPort
    private int port;

    @Test
    void testHealthEndpointIsAccessible() {
        ResponseEntity<String> response = getHealth();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testHealthEndpointReturnsUpStatus() {
        ResponseEntity<String> response = getHealth();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"status\":\"UP\""));
    }

    private ResponseEntity<String> getHealth() {
        return RestClient.create()
                .get()
                .uri("http://localhost:%d/actuator/health".formatted(port))
                .retrieve()
                .toEntity(String.class);
    }
}
