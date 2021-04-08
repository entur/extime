package no.rutebanken.extime;

import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;


/**
 * Integration test base class that keeps CamelContext between tests. Useful when camel is not used expicitly in tests.
 */
@CamelSpringBootTest
@ActiveProfiles({"default", "local-disk-blobstore", "google-pubsub-emulator"})
@SpringBootTest(classes = {App.class}, properties = {
        "avinor.timetable.scheduler.consumer=direct:start"
})
public abstract class ExtimeRouteBuilderIntegrationTestBase {


}
