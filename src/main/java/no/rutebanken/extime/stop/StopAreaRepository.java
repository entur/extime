package no.rutebanken.extime.stop;

import no.rutebanken.extime.loader.NetexDatasetLoader;
import no.rutebanken.extime.services.MardukBlobStoreService;
import no.rutebanken.extime.util.ExtimeException;
import no.rutebanken.extime.util.Retry;
import no.rutebanken.extime.util.RetrySettings;
import org.entur.netex.index.api.NetexEntitiesIndex;
import org.rutebanken.netex.model.KeyValueStructure;
import org.rutebanken.netex.model.Quay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads the NeTEx stop dataset from the marduk bucket and indexes its quays by Avinor local reference.
 *
 * <p>Replaces {@code direct:refreshStops}, {@code direct:downloadNetexStopDataset} and
 * {@code direct:getMardukBlob}. Camel passed the resulting map to the converter as an exchange property;
 * it is now a return value.
 */
@Component
public class StopAreaRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(StopAreaRepository.class);

    private final MardukBlobStoreService mardukBlobStoreService;
    private final NetexDatasetLoader netexDatasetLoader = new NetexDatasetLoader();
    private final RetrySettings retrySettings;
    private final String airportsExportFilename;

    public StopAreaRepository(
            MardukBlobStoreService mardukBlobStoreService,
            RetrySettings retrySettings,
            @Value("${extime.netex.airports.export.filename:tiamat/Airports_latest.zip}") String airportsExportFilename) {
        this.mardukBlobStoreService = mardukBlobStoreService;
        this.retrySettings = retrySettings;
        this.airportsExportFilename = airportsExportFilename;
    }

    /**
     * @return the quays of the NeTEx stop dataset, keyed by their {@code imported-id}, e.g.
     * {@code ["AVI:Quay:234" -> Quay]}.
     */
    public Map<String, Quay> loadQuayMap() {
        LOGGER.debug("Refreshing stop areas.");
        NetexEntitiesIndex index = Retry.withRetry(retrySettings,
                "Downloading the NeTEx stop dataset " + airportsExportFilename,
                this::downloadAndLoad);
        Map<String, Quay> quayMap = buildAvinorLocalReferenceToQuayMap(index);
        LOGGER.debug("Refreshed stop areas.");
        return quayMap;
    }

    private NetexEntitiesIndex downloadAndLoad() {
        LOGGER.info("Downloading NeTEx Stop dataset {}", airportsExportFilename);
        try (InputStream stopDataset = mardukBlobStoreService.getBlob(airportsExportFilename)) {
            if (stopDataset == null) {
                // Camel's stop() abandoned the exchange here, so the export produced nothing and reported
                // success. Same outcome, but now with a failure to alert on.
                throw new ExtimeException("NeTEx Stopfile not found: " + airportsExportFilename);
            }
            LOGGER.info("Loading NeTEx entries index for airports");
            return netexDatasetLoader.load(stopDataset);
        } catch (IOException e) {
            throw new ExtimeException("Could not read the NeTEx stop dataset " + airportsExportFilename, e);
        }
    }

    private Map<String, Quay> buildAvinorLocalReferenceToQuayMap(NetexEntitiesIndex index) {
        Function<Quay, String> findAvinorLocalReference = quay -> {
            if (quay.getKeyList() == null) {
                return null;
            }
            return quay.getKeyList().getKeyValue().stream()
                    .filter(keyValueStructure -> keyValueStructure.getKey().equals("imported-id"))
                    .map(KeyValueStructure::getValue)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);
        };

        return index.getQuayIndex().getAllVersions()
                .keySet()
                .stream()
                .map(quays -> index.getQuayIndex().getLatestVersion(quays))
                .filter(quay -> {
                    String localReference = findAvinorLocalReference.apply(quay);
                    if (localReference == null) {
                        LOGGER.warn("Skipping quay {} with blank or missing imported-id", quay.getId());
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toMap(findAvinorLocalReference, Function.identity()));
    }
}
