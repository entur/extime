package no.rutebanken.extime.routes.avinor;

import com.google.pubsub.v1.PubsubMessage;
import no.rutebanken.extime.ExtimeRouteBuilderIntegrationTestBase;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.extime.Constants.HEADER_MESSAGE_FILE_HANDLE;
import static no.rutebanken.extime.converter.CommonDataToNetexConverter.PROPERTY_NSR_QUAY_MAP;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_MESSAGE_FILE_NAME;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_MESSAGE_PROVIDER_ID;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_MESSAGE_USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for Avinor timetable processing using dump input mode.
 * 
 * <p>This test validates the complete NeTEx conversion pipeline by processing pre-captured
 * flight data from src/test/resources/testdata. The dump input mode (avinor.timetable.dump.input=true)
 * allows testing without making live API calls to Avinor's web service.</p>
 * 
 * <p>The test verifies that:</p>
 * <ul>
 *   <li>XML flight data files are successfully read from the testdata directory</li>
 *   <li>Flight events are mapped and converted to NeTEx format</li>
 *   <li>NeTEx XML files are generated in the output directory</li>
 *   <li>Files are compressed into a zip archive for delivery</li>
 *   <li>PubSub notification is sent to Marduk with expected headers</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {AvinorTimetableRouteBuilder.class}, properties = {
        "avinor.timetable.scheduler.consumer=direct:start",
        "avinor.timetable.period.forward=14",
        "avinor.timetable.dump.input=true",
        "avinor.timetable.dump.input.path=src/test/resources/testdata",
        "avinor.airports.small=EVE,KRS,MOL,SOG,TOS,ALF,HFT,SKN,AES,FAN,LKN,BNN,BVG,HMR,FRO,HVG,SRP,VRY,VAW,MEH",
        "avinor.airports.large=BGO,BOO,SVG,TRD,OSL",
        "netex.generated.output.path=target/netex-dump-test",
        "netex.compressed.output.path=target/marduk-dump-test",
        "queue.upload.destination.name=MockMardukQueue"
})
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AvinorTimetableDumpInputIntegrationTest extends ExtimeRouteBuilderIntegrationTestBase {

    @Value("${queue.upload.destination.name}")
    private String notificationQueue;

    @Test
    void testTimetableProcessingFromDumpInput() throws Exception {

        AdviceWith.adviceWith(context, "AvinorTimetableSchedulerStarter", a -> {
            a.weaveByToUri("direct:refreshStops").replace().process(
                    exchange -> exchange.setProperty(PROPERTY_NSR_QUAY_MAP, new HashMap<>())
            );
        });

        context.start();
        startTemplate.sendBody(null);

        // Verify that NeTEx files were generated
        Path netexOutputPath = Paths.get("target/netex-dump-test");
        assertTrue(Files.exists(netexOutputPath), "NeTEx output directory should exist");
        
        // Verify that compressed output was created
        Path compressedOutputPath = Paths.get("target/marduk-dump-test");
        assertTrue(Files.exists(compressedOutputPath), "Compressed output directory should exist");
        
        // Verify at least one zip file was created
        File[] zipFiles = compressedOutputPath.toFile().listFiles((dir, name) -> name.endsWith(".zip"));
        assertTrue(zipFiles != null && zipFiles.length > 0, "At least one NeTEx zip file should be generated");

        // Verify PubSub notification was sent with expected headers
        List<PubsubMessage> messages = pubSubTemplate.pullAndAck(notificationQueue, 1, false);
        assertEquals(1, messages.size(), "Expected exactly one PubSub message");
        
        PubsubMessage pubsubMessage = messages.getFirst();
        assertTrue(pubsubMessage.getData().isEmpty(), "PubSub message body should be empty");
        
        Map<String, String> headers = pubsubMessage.getAttributesMap();
        assertNotNull(headers.get(Exchange.FILE_NAME), "FILE_NAME header should be present");
        assertNotNull(headers.get(HEADER_MESSAGE_PROVIDER_ID), "Provider ID header should be present");
        assertNotNull(headers.get(HEADER_MESSAGE_FILE_HANDLE), "File handle header should be present");
        assertNotNull(headers.get(HEADER_MESSAGE_FILE_NAME), "File name header should be present");
        assertNotNull(headers.get(HEADER_MESSAGE_USERNAME), "Username header should be present");
    }
}
