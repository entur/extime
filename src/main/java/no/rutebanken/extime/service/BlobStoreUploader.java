package no.rutebanken.extime.service;

import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.language.Simple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class BlobStoreUploader {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final long FILE_SIZE_LIMIT = 1_000_000;
    private static final int BUFFER_CHUNK_SIZE = 1024;

    @Autowired private Storage storage;
    @Value("${blobstore.gcs.bucket.name}") private String bucketName;
    @Value("${blobstore.gcs.blob.path}") private String blobPath;

    public void uploadFile(@Simple(value = "${properties:netex.compressed.output.path}") String compressedOutputPath,
                           @Header(Exchange.FILE_NAME) String compressedFileName) throws Exception {
        Path filePath = Paths.get(compressedOutputPath, compressedFileName);
        String contentType = Files.probeContentType(filePath);
        String blobIdName = blobPath + filePath.getFileName().toString();

        BlobId blobId = BlobId.of(bucketName, blobIdName);
        logger.debug("blobId: {}", blobId.toString());

        BlobInfo blobInfo = BlobInfo.builder(blobId).contentType(contentType).build();
        //BlobInfo blobInfo = BlobInfo.builder(blobId).contentType("application/octet-stream").build();
        logger.debug("blobInfo: {}", blobInfo);

        //builder.acl(ImmutableList.of(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER))); // must have?

        if (Files.size(filePath) > FILE_SIZE_LIMIT) {

            try (WriteChannel writer = storage.writer(blobInfo)) {

                byte[] buffer = new byte[BUFFER_CHUNK_SIZE];

                try (InputStream inputStream = Files.newInputStream(filePath)) {
                    int limit;
                    while ((limit = inputStream.read(buffer)) >= 0) {
                        writer.write(ByteBuffer.wrap(buffer, 0, limit));
                    }
                }
            }
        } else {
            byte[] bytes = Files.readAllBytes(filePath);
            storage.create(blobInfo, bytes);
        }

        logger.info("Blob was stored in bucket '" + bucketName + "'");
    }

}
