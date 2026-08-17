package no.rutebanken.extime.services;

import org.rutebanken.helper.storage.repository.BlobStoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Operations on blobs in the marduk exchange bucket.
 */
@Service
public class MardukExchangeBlobStoreService extends AbstractBlobStoreService {


    public MardukExchangeBlobStoreService(@Value("${blobstore.gcs.marduk-exchange.container.name:marduk-exchange}") String containerName,
                                          BlobStoreRepository repository) {
        super(containerName, repository);
    }

    public void uploadBlob(String targetFile, InputStream sourceFile) {
        repository.uploadNewBlob(targetFile, sourceFile);
    }
}
