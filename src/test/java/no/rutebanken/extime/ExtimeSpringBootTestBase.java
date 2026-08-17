package no.rutebanken.extime;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the application for tests that need the Spring context.
 *
 * <p>No PubSub emulator: {@code spring.cloud.gcp.pubsub.emulator-host} in the test properties points at
 * an unreachable host, which is what makes spring-cloud-gcp skip its credential lookup. Nothing connects.
 * The transport is covered once, by {@link no.rutebanken.extime.pubsub.MardukNotifierPubSubTest}.
 */
@SpringBootTest(classes = App.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({ "default", "in-memory-blobstore" })
public abstract class ExtimeSpringBootTestBase {
}
