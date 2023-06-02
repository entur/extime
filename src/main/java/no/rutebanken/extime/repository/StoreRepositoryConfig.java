package no.rutebanken.extime.repository;

import com.google.cloud.storage.Storage;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("gcs-blobstore")
public class StoreRepositoryConfig {

    @Value("${blobstore.gcs.credential.path:#{null}}")
    private String credentialPath;

    @Value("${blobstore.gcs.project.id}")
    private String projectId;

    @Bean
    public Storage getStorage() {
        if (credentialPath == null || credentialPath.isEmpty()) {
            //Used default gcp credentials
            return BlobStoreHelper.getStorage(projectId);
        } else {
            return BlobStoreHelper.getStorage(credentialPath, projectId);
        }
    }
}
