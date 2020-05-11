package no.rutebanken.extime;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;


/**
 * Integration test base class that restarts CamelContext before each test. To be used when camel is used expicitly in tests.
 */
@RunWith(CamelSpringBootRunner.class)
@ActiveProfiles({"default", "local-disk-blobstore", "google-pubsub-emulator"})
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class ExtimeCamelRouteBuilderIntegrationTestBase {

    @Autowired
    protected ModelCamelContext context;

    @Autowired
    protected PubSubTemplate pubSubTemplate;

    @Produce(uri = "direct:start")
    protected ProducerTemplate startTemplate;

}
