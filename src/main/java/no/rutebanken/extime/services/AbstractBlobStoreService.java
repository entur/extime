package no.rutebanken.extime.services;

import no.rutebanken.extime.repository.BlobStoreRepository;
import org.apache.camel.Header;

import java.io.InputStream;

import static no.rutebanken.extime.Constants.HEADER_MESSAGE_FILE_HANDLE;

public abstract class AbstractBlobStoreService {

    protected final BlobStoreRepository repository;

    protected AbstractBlobStoreService(String containerName, BlobStoreRepository repository) {
        this.repository = repository;
        this.repository.setContainerName(containerName);
    }

    public InputStream getBlob(@Header(value = HEADER_MESSAGE_FILE_HANDLE) String name) {
        return repository.getBlob(name);
    }
}