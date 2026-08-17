package no.rutebanken.extime.pubsub;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.pubsub.v1.PubsubMessage;
import no.rutebanken.extime.App;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.gcloud.PubSubEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one test that starts a real emulator, and it checks only the transport: that a notification
 * reaches the queue carrying the attributes marduk matches on.
 *
 * <p>What the values mean is covered by {@code TimetableExportJobTest}, which runs the whole pipeline
 * without an emulator.
 */
@SpringBootTest(classes = App.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({ "default", "in-memory-blobstore", "google-pubsub-autocreate" })
@Testcontainers
class MardukNotifierPubSubTest {

    @Container
    private static final PubSubEmulatorContainer PUBSUB_EMULATOR = new PubSubEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/cloud-sdk:emulators"));

    @DynamicPropertySource
    static void emulatorProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gcp.pubsub.emulator-host", PUBSUB_EMULATOR::getEmulatorEndpoint);
    }

    @Autowired
    private MardukNotifier mardukNotifier;

    @Autowired
    private PubSubTemplate pubSubTemplate;

    @Value("${queue.upload.destination.name}")
    private String notificationQueue;

    @Test
    void publishesTheAttributesMardukMatchesOn() {
        mardukNotifier.notifyMarduk(
                "avinor-netex_20260814-025800.zip",
                "inbound/received/avi/avinor-netex_20260814-025800.zip",
                "a-correlation-id");

        List<PubsubMessage> messages = pubSubTemplate.pullAndAck(notificationQueue, 1, false);
        assertThat(messages).hasSize(1);

        PubsubMessage message = messages.getFirst();
        assertThat(message.getData().isEmpty()).as("marduk reads the notification from the attributes").isTrue();

        Map<String, String> attributes = message.getAttributesMap();
        assertThat(attributes).containsExactlyInAnyOrderEntriesOf(Map.of(
                "RutebankenCorrelationId", "a-correlation-id",
                "RutebankenFileHandle", "inbound/received/avi/avinor-netex_20260814-025800.zip",
                "RutebankenProviderId", "21",
                "RutebankenFileName", "avinor-netex_20260814-025800.zip",
                "RutebankenUsername", "Extime",
                "CamelFileName", "avinor-netex_20260814-025800.zip"));
    }
}
