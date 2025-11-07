package no.rutebanken.extime.services;

import no.rutebanken.extime.Constants;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.rutebanken.helper.storage.repository.BlobStoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Operations on blobs in the marduk exchange bucket.
 */
@Service
public class MardukExchangeBlobStoreService extends AbstractBlobStoreService {


    public MardukExchangeBlobStoreService(@Value("${blobstore.gcs.marduk-exchange.container.name:marduk-exchange}") String containerName,
                                          BlobStoreRepository repository) {
        super(containerName, repository);
    }

    public void uploadBlob(@Header(Constants.HEADER_MESSAGE_FILE_HANDLE) String targetFile,
                           @Header(Exchange.FILE_NAME_PRODUCED) Path sourceFile
    ) throws IOException {
        try (InputStream inputStream = Files.newInputStream(sourceFile)) {
            repository.uploadNewBlob(targetFile, inputStream);
        }
    }
}
