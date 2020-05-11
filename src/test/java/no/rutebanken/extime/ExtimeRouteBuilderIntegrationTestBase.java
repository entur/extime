package no.rutebanken.extime;

import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;


/**
 * Integration test base class that keeps CamelContext between tests. Useful when camel is not used expicitly in tests.
 */
@RunWith(CamelSpringBootRunner.class)
@ActiveProfiles({"default", "local-disk-blobstore", "google-pubsub-emulator"})
@SpringBootTest(classes = {App.class}, properties = {
        "spring.config.name=application,netex-static-data",
        "avinor.timetable.scheduler.consumer=direct:start"
})
public abstract class ExtimeRouteBuilderIntegrationTestBase {


}
