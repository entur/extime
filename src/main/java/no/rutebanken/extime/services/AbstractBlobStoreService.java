package no.rutebanken.extime.services;

import org.rutebanken.helper.storage.repository.BlobStoreRepository;

import java.io.InputStream;

public abstract class AbstractBlobStoreService {

    protected final BlobStoreRepository repository;

    protected AbstractBlobStoreService(String containerName, BlobStoreRepository repository) {
        this.repository = repository;
        this.repository.setContainerName(containerName);
    }

    public InputStream getBlob(String name) {
        return repository.getBlob(name);
    }
}
