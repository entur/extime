package no.rutebanken.extime.config;

import com.google.cloud.AuthCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
//@Profile("test")
public class StorageConfig {

    @Value("${blobstore.gcs.credential.path}")
    private String credentialPath;

    @Value("${blobstore.gcs.project.id}")
    private String projectId;

    @Bean
    public Storage storage() {
        try {
            return StorageOptions.builder()
                    .projectId(projectId)
                    .authCredentials(AuthCredentials.createForJson(new FileInputStream(credentialPath)))
                    //.connectTimeout(3000)
                    //.readTimeout(3000)
                    .build()
                    .service();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}