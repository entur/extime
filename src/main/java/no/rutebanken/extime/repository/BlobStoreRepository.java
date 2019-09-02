package no.rutebanken.extime.repository;


/**
 * Repository interface for managing binary files.
 * The main implementation {@link GcsBlobStoreRepository} targets Google Cloud Storage.
 * A simple implementation {@link LocalDiskBlobStoreRepository} is available for testing in a local environment
 */
public interface BlobStoreRepository {

    void uploadBlob(String compressedFileName, String compressedFilePath, String correlationId);
}
