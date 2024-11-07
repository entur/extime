package no.rutebanken.extime.services;

import no.rutebanken.extime.Constants;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.rutebanken.helper.storage.BlobStoreException;
import org.rutebanken.helper.storage.repository.BlobStoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

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
                           @Header(Exchange.FILE_NAME_PRODUCED) String sourceFile
                           ) {

        try {
            repository.uploadNewBlob(targetFile, new FileInputStream(sourceFile));
        } catch (FileNotFoundException e) {
            throw new BlobStoreException(e);
        }
    }
}
