package no.rutebanken.extime.stop;

import no.rutebanken.extime.services.MardukBlobStoreService;
import no.rutebanken.extime.util.ExtimeException;
import no.rutebanken.extime.util.RetrySettings;
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.Quay;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The quay map is what ties an Avinor airport to its NSR stop place, so a quay silently missing from it
 * means the exported NeTEx keeps extime's own placeholder reference instead of the national one.
 */
class StopAreaRepositoryTest {

    private static final String AIRPORTS_ZIP = "tiamat/Airports_latest.zip";

    @Test
    void indexesQuaysByTheirAvinorImportedId() {
        StopAreaRepository repository = repositoryReturning(netexArchive("""
                <Quay version="1" id="NSR:Quay:11111">
                  <keyList>
                    <KeyValue><Key>imported-id</Key><Value>AVI:Quay:BGO</Value></KeyValue>
                  </keyList>
                </Quay>
                <Quay version="1" id="NSR:Quay:22222">
                  <keyList>
                    <KeyValue><Key>imported-id</Key><Value>AVI:Quay:TRD</Value></KeyValue>
                  </keyList>
                </Quay>
                """));

        Map<String, Quay> quayMap = repository.loadQuayMap();

        assertThat(quayMap).containsOnlyKeys("AVI:Quay:BGO", "AVI:Quay:TRD");
        assertThat(quayMap.get("AVI:Quay:BGO").getId()).isEqualTo("NSR:Quay:11111");
    }

    @Test
    void skipsQuaysWithoutAnImportedId() {
        StopAreaRepository repository = repositoryReturning(netexArchive("""
                <Quay version="1" id="NSR:Quay:11111">
                  <keyList>
                    <KeyValue><Key>imported-id</Key><Value>AVI:Quay:BGO</Value></KeyValue>
                  </keyList>
                </Quay>
                <Quay version="1" id="NSR:Quay:33333"/>
                <Quay version="1" id="NSR:Quay:44444">
                  <keyList>
                    <KeyValue><Key>imported-id</Key><Value> </Value></KeyValue>
                  </keyList>
                </Quay>
                """));

        assertThat(repository.loadQuayMap()).containsOnlyKeys("AVI:Quay:BGO");
    }

    @Test
    void failsWhenTheStopDatasetIsMissing() {
        StopAreaRepository repository = repositoryReturning(null);

        assertThatThrownBy(repository::loadQuayMap)
                .isInstanceOf(ExtimeException.class)
                .hasMessageContaining(AIRPORTS_ZIP);
    }

    private static StopAreaRepository repositoryReturning(byte[] archive) {
        MardukBlobStoreService blobStore = mock(MardukBlobStoreService.class);
        when(blobStore.getBlob(any())).thenReturn(archive == null ? null : new ByteArrayInputStream(archive));
        return new StopAreaRepository(blobStore, new RetrySettings(0, Duration.ofMillis(1), 1), AIRPORTS_ZIP);
    }

    /**
     * A NeTEx stop dataset holding the given quays, packaged the way tiamat publishes it.
     */
    private static byte[] netexArchive(String quays) {
        String netex = """
                <?xml version="1.0" encoding="UTF-8"?>
                <PublicationDelivery version="1.15:NO-NeTEx-networktimetable:1.5" xmlns="http://www.netex.org.uk/netex">
                  <PublicationTimestamp>2026-08-17T00:00:00</PublicationTimestamp>
                  <ParticipantRef>NSR</ParticipantRef>
                  <dataObjects>
                    <SiteFrame version="1" id="NSR:SiteFrame:1">
                      <stopPlaces>
                        <StopPlace version="1" id="NSR:StopPlace:99999">
                          <Name>Test lufthavn</Name>
                          <quays>
                %s
                          </quays>
                        </StopPlace>
                      </stopPlaces>
                    </SiteFrame>
                  </dataObjects>
                </PublicationDelivery>
                """.formatted(quays.indent(12));

        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(archive)) {
            zip.putNextEntry(new ZipEntry("stops.xml"));
            zip.write(netex.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the test NeTEx archive", e);
        }
        return archive.toByteArray();
    }
}
