package no.rutebanken.extime.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * Simple file-based blob store repository for testing purpose.
 * The system property blobstore.local.folder specifies the target file location.
 */
@Component
@Profile("local-disk-blobstore")
public class LocalDiskBlobStoreRepository implements BlobStoreRepository {


    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${blobstore.local.folder:files/blob}")
    private String baseFolder;

    @Override
    public void uploadBlob(String compressedFileName, String compressedFilePath, String correlationId) {

        logger.debug("upload blob called in local-disk blob store on {}", compressedFilePath );
        try {

            Path sourceFilePath = Paths.get(compressedFilePath);
            Path targetFolderPath= Paths.get(baseFolder);
            Path targetFilePath= targetFolderPath.resolve(compressedFileName);

            Files.createDirectories(targetFolderPath);
            Files.deleteIfExists(targetFilePath);
            try (InputStream inputStream = Files.newInputStream(sourceFilePath)) {
                Files.copy(inputStream, targetFilePath);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
