package no.rutebanken.extime.repository;

import no.rutebanken.extime.util.ExtimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
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
@Scope("prototype")
@Profile("local-disk-blobstore")
public class LocalDiskBlobStoreRepository implements BlobStoreRepository {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private String containerName;

    @Value("${blobstore.local.folder:files/blob}")
    private String baseFolder;

    @Override
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    private String getContainerFolder() {
        return baseFolder + File.separator + containerName;
    }

    @Override
    public InputStream getBlob(String objectName) {
        logger.debug("get blob called in local-disk blob store on {}", objectName);
        Path path = Paths.get(getContainerFolder()).resolve(objectName);
        if (!path.toFile().exists()) {
            logger.debug("getBlob(): File not found in local-disk blob store: {} ", path);
            return null;
        }
        logger.debug("getBlob(): File found in local-disk blob store: {} ", path);
        try {
            // converted as ByteArrayInputStream so that Camel stream cache can reopen it
            // since ByteArrayInputStream.close() does nothing
            return new ByteArrayInputStream(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new ExtimeException(e);
        }
    }

    @Override
    public void uploadBlob(String targetFile,
                           String sourceFile,
                           String correlationId) {

        logger.debug("upload blob called in local-disk blob store on {}", sourceFile );
        try {

            Path sourceFilePath = Paths.get(sourceFile);
            Path targetFolderPath= Paths.get(getContainerFolder());
            Path targetFilePath= targetFolderPath.resolve(targetFile);

            Files.createDirectories(targetFilePath.getParent());
            Files.deleteIfExists(targetFilePath);
            try (InputStream inputStream = Files.newInputStream(sourceFilePath)) {
                Files.copy(inputStream, targetFilePath);
            }
        } catch (IOException e) {
            throw new ExtimeException(e);
        }
    }

}
