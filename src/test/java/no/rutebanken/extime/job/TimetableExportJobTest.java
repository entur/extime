package no.rutebanken.extime.job;

import no.rutebanken.extime.ExtimeSpringBootTestBase;
import no.rutebanken.extime.pubsub.MardukNotifier;
import no.rutebanken.extime.stop.StopAreaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the whole export in one JVM, from the captured Avinor responses in {@code src/test/resources/testdata}
 * to the archive in the blob store, with no emulator and no network.
 *
 * <p>This is where the pipeline is covered. The two collaborators that reach outside are stubbed: the
 * stop dataset, which the in-memory blob store does not hold, and the notification, whose transport is
 * covered once by {@code MardukNotifierPubSubTest}.
 */
@SpringBootTest(properties = {
        "avinor.timetable.dump.input=true",
        "avinor.timetable.dump.input.path=src/test/resources/testdata",
        "netex.generated.output.path=target/netex-dump-test",
        "netex.compressed.output.path=target/marduk-dump-test",
        "queue.upload.destination.name=MockMardukQueue"
})
class TimetableExportJobTest extends ExtimeSpringBootTestBase {

    @MockitoBean
    private StopAreaRepository stopAreaRepository;

    @MockitoBean
    private MardukNotifier mardukNotifier;

    @Autowired
    private TimetableExportJob timetableExportJob;

    @Autowired
    private Map<String, Map<String, byte[]>> blobsInContainers;

    @Test
    void exportsTheArchiveAndNotifiesMarduk() throws Exception {
        when(stopAreaRepository.loadQuayMap()).thenReturn(Map.of());

        timetableExportJob.export();

        Path netexOutputPath = Path.of("target/netex-dump-test");
        assertThat(netexOutputPath).isDirectory();
        assertThat(netexOutputPath.resolve("_avinor_common_elements.xml")).isRegularFile();
        long generatedFiles;
        try (var netexFiles = Files.list(netexOutputPath)) {
            generatedFiles = netexFiles.filter(p -> p.toString().endsWith(".xml")).count();
        }
        assertThat(generatedFiles).as("a NeTEx file per line, plus the common elements").isGreaterThan(1);

        ArgumentCaptor<String> fileName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> fileHandle = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        verify(mardukNotifier).notifyMarduk(fileName.capture(), fileHandle.capture(), correlationId.capture());

        assertThat(fileName.getValue()).startsWith("avinor-netex_").endsWith(".zip");
        // The path layout marduk reads the archive back from.
        assertThat(fileHandle.getValue()).isEqualTo("inbound/received/avi/" + fileName.getValue());
        assertThat(correlationId.getValue()).isNotBlank();

        byte[] uploaded = blobsInContainers.get("marduk-exchange").get(fileHandle.getValue());
        assertThat(uploaded)
                .as("the archive is uploaded under the file handle marduk is told about")
                .isNotNull();
        assertThat(entryNamesOf(uploaded))
                .as("marduk replaces the previous dataset with this archive, so it must hold every generated file")
                .hasSize((int) generatedFiles)
                .contains("_avinor_common_elements.xml");
    }

    private static List<String> entryNamesOf(byte[] archive) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
