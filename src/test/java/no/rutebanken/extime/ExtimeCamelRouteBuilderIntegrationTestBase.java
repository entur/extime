package no.rutebanken.extime;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;


/**
 * Integration test base class that restarts CamelContext before each test. To be used when camel is used expicitly in tests.
 */
@CamelSpringBootTest
@ActiveProfiles({"default", "in-memory-blobstore", "google-pubsub-emulator", "google-pubsub-autocreate"})
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class ExtimeCamelRouteBuilderIntegrationTestBase {

    @Autowired
    protected CamelContext context;

    @Autowired
    protected PubSubTemplate pubSubTemplate;

    @Produce("direct:start")
    protected ProducerTemplate startTemplate;


    @AfterEach
    void stopContext() {
        context.stop();
    }

}
