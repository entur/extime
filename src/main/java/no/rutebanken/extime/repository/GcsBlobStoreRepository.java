package no.rutebanken.extime.repository;

import com.google.cloud.storage.Storage;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Blob store repository targeting Google Cloud Storage.
 */
@Component
@Profile("gcs-blobstore")
public class GcsBlobStoreRepository implements BlobStoreRepository {

    private static final Logger log = LoggerFactory.getLogger(GcsBlobStoreRepository.class);

    private String containerName;

    private final Storage storage;

    @Value("${blobstore.blob.path}")
    private String blobPath;

    @Value("${blobstore.provider.id}")
    private String providerId;

    public GcsBlobStoreRepository(Storage storage) {
        this.storage = storage;
    }

    @Override
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    @Override
    public InputStream getBlob(String name) {
        return BlobStoreHelper.getBlob(storage, containerName, name);
    }

    @Override
    public void uploadBlob(String compressedFileName, String compressedFilePath, String correlationId) {

        try {
            log.info("Placing file '{}' from provider with id '{}' and correlation id '{}' in blob store.",
                    compressedFileName, providerId, correlationId);

            Path filePath = Paths.get(compressedFilePath);
            String blobIdName = blobPath + compressedFileName;
            log.info("Creating blob : {}", blobIdName);

            try (InputStream inputStream = Files.newInputStream(filePath)) {
                BlobStoreHelper.createNew(storage, containerName, blobIdName, inputStream, false);
            }
            log.info("Stored blob with name '{}' and size '{}' in bucket '{}'", filePath.getFileName(), Files.size(filePath), containerName);
        } catch (Exception e) {
            log.warn("Failed to put file '{}' in blobstore", compressedFileName, e);
        }
    }

}
