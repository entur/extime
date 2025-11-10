package no.rutebanken.extime.routes.avinor;

import no.rutebanken.extime.ExtimeRouteBuilderIntegrationTestBase;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import static no.rutebanken.extime.converter.CommonDataToNetexConverter.PROPERTY_NSR_QUAY_MAP;
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
 *   <li>Marduk notification is triggered upon completion</li>
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

    @EndpointInject("mock:notifyMarduk")
    protected MockEndpoint mockNotifyMarduk;

    @Test
    void testTimetableProcessingFromDumpInput() throws Exception {

        AdviceWith.adviceWith(context, "AvinorTimetableSchedulerStarter", a -> {
            a.weaveByToUri("direct:refreshStops").replace().process(
                    exchange -> exchange.setProperty(PROPERTY_NSR_QUAY_MAP, new HashMap<>())
            );
        });

        AdviceWith.adviceWith(context, "CompressNetexAndSendToStorage", a ->
            a.weaveByToUri("direct:notifyMarduk").replace().to("mock:notifyMarduk")
        );

        mockNotifyMarduk.expectedMinimumMessageCount(1);
        context.start();
        startTemplate.sendBody(null);
        mockNotifyMarduk.assertIsSatisfied();

        // Verify that NeTEx files were generated
        Path netexOutputPath = Paths.get("target/netex-dump-test");
        assertTrue(Files.exists(netexOutputPath), "NeTEx output directory should exist");
        
        // Verify that compressed output was created
        Path compressedOutputPath = Paths.get("target/marduk-dump-test");
        assertTrue(Files.exists(compressedOutputPath), "Compressed output directory should exist");
        
        // Verify at least one zip file was created
        File[] zipFiles = compressedOutputPath.toFile().listFiles((dir, name) -> name.endsWith(".zip"));
        assertTrue(zipFiles != null && zipFiles.length > 0, "At least one NeTEx zip file should be generated");
    }
}
