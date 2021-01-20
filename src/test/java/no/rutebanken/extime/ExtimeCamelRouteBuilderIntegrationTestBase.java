package no.rutebanken.extime;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;


/**
 * Integration test base class that restarts CamelContext before each test. To be used when camel is used expicitly in tests.
 */
@CamelSpringBootTest
@ActiveProfiles({"default", "local-disk-blobstore", "google-pubsub-emulator"})
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class ExtimeCamelRouteBuilderIntegrationTestBase {

    @Autowired
    protected ModelCamelContext context;

    @Autowired
    protected PubSubTemplate pubSubTemplate;

    @Produce("direct:start")
    protected ProducerTemplate startTemplate;

}
