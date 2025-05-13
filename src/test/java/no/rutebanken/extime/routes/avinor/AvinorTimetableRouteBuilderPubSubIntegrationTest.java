package no.rutebanken.extime.routes.avinor;

import com.google.pubsub.v1.PubsubMessage;
import no.rutebanken.extime.ExtimeRouteBuilderIntegrationTestBase;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;

import static no.rutebanken.extime.Constants.HEADER_MESSAGE_FILE_HANDLE;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_MESSAGE_FILE_NAME;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_MESSAGE_PROVIDER_ID;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_MESSAGE_USERNAME;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {AvinorTimetableRouteBuilder.class}, properties = {
        "avinor.timetable.scheduler.consumer=direct:start",
        "avinor.timetable.period.forward=14",
        "avinor.timetable.feed.endpoint=mock:timetableFeedEndpoint",
        "avinor.airports.small=EVE,KRS,MOL,SOG,TOS",
        "avinor.airports.large=BGO,BOO,SVG,TRD",
        "netex.generated.output.path=target/netex-mock",
        "netex.compressed.output.path=target/marduk-mock",
        "avinor.timetable.dump.output.path=target/flights"
})
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AvinorTimetableRouteBuilderPubSubIntegrationTest extends ExtimeRouteBuilderIntegrationTestBase {

    @Value("${queue.upload.destination.name}")
    private String notificationQueue;

    @Produce("direct:compressNetexAndSendToStorage")
    protected ProducerTemplate compressNetexAndSendToStorageTemplate;

    @Test
    void testNotifyMarduk() {

        context.start();
        compressNetexAndSendToStorageTemplate.sendBody("");

        List<PubsubMessage> messages = pubSubTemplate.pullAndAck(notificationQueue, 1, false);
        Assertions.assertEquals(1, messages.size());
        PubsubMessage pubsubMessage = messages.getFirst();
        Assertions.assertTrue(pubsubMessage.getData().isEmpty());
        Map<String, String> headers = pubsubMessage.getAttributesMap();
        Assertions.assertNotNull(headers.get(Exchange.FILE_NAME));
        Assertions.assertNotNull(headers.get(HEADER_MESSAGE_PROVIDER_ID));
        Assertions.assertNotNull(headers.get(HEADER_MESSAGE_FILE_HANDLE));
        Assertions.assertNotNull(headers.get(HEADER_MESSAGE_FILE_NAME));
        Assertions.assertNotNull(headers.get(HEADER_MESSAGE_USERNAME));

    }


}