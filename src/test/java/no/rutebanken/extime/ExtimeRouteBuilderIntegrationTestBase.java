package no.rutebanken.extime;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;


@CamelSpringBootTest
@ActiveProfiles({"default", "in-memory-blobstore", "google-pubsub-autocreate"})
@SpringBootTest(classes = {App.class}, properties = {
        "avinor.timetable.scheduler.consumer=direct:start"
})
@Testcontainers
public abstract class ExtimeRouteBuilderIntegrationTestBase {

    private static PubSubEmulatorContainer pubsubEmulator;

    @Autowired
    protected CamelContext context;

    @Autowired
    protected PubSubTemplate pubSubTemplate;


    @Produce("direct:start")
    protected ProducerTemplate startTemplate;

    @BeforeAll
    public static void init() {
        pubsubEmulator =
                new PubSubEmulatorContainer(
                        DockerImageName.parse(
                                "gcr.io/google.com/cloudsdktool/cloud-sdk:emulators"
                        )
                );
        pubsubEmulator.start();
    }

    @DynamicPropertySource
    static void emulatorProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.cloud.gcp.pubsub.emulator-host",
                pubsubEmulator::getEmulatorEndpoint
        );
        registry.add(
                "camel.component.google-pubsub.endpoint",
                pubsubEmulator::getEmulatorEndpoint
        );
    }

    @AfterAll
    public static void tearDown() {
        pubsubEmulator.stop();
    }

}
