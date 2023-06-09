package no.rutebanken.extime.repository;

import java.io.InputStream;

/**
 * Repository interface for managing binary files.
 * The main implementation {@link GcsBlobStoreRepository} targets Google Cloud Storage.
 * A simple implementation {@link LocalDiskBlobStoreRepository} is available for testing in a local environment
 */
public interface BlobStoreRepository {

    InputStream getBlob(String objectName);

    void uploadBlob(String compressedFileName,
                    String compressedFilePath,
                    String correlationId);

    void setContainerName(String containerName);
}
