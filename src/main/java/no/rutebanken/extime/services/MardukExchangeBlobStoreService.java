package no.rutebanken.extime.services;

import no.rutebanken.extime.repository.BlobStoreRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static no.rutebanken.extime.Constants.HEADER_MESSAGE_CORRELATION_ID;

/**
 * Operations on blobs in the marduk exchange bucket.
 */
@Service
public class MardukExchangeBlobStoreService extends AbstractBlobStoreService {

    public MardukExchangeBlobStoreService(@Value("${blobstore.gcs.marduk-exchange.container.name:marduk-exchange}") String containerName,
                                          @Autowired BlobStoreRepository repository) {
        super(containerName, repository);
    }

    public void uploadBlob(@Header(Exchange.FILE_NAME) String compressedFileName,
                           @Header(Exchange.FILE_NAME_PRODUCED) String compressedFilePath,
                           @Header(HEADER_MESSAGE_CORRELATION_ID) String correlationId) {
        repository.uploadBlob(compressedFileName, compressedFilePath, correlationId);
    }
}
