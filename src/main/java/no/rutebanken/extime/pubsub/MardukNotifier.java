package no.rutebanken.extime.pubsub;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import no.rutebanken.extime.util.ExtimeException;
import no.rutebanken.extime.util.Retry;
import no.rutebanken.extime.util.RetrySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static no.rutebanken.extime.Constants.EXTIME_USERNAME;
import static no.rutebanken.extime.Constants.HEADER_LEGACY_CAMEL_FILE_NAME;
import static no.rutebanken.extime.Constants.HEADER_MESSAGE_CORRELATION_ID;
import static no.rutebanken.extime.Constants.HEADER_MESSAGE_FILE_HANDLE;
import static no.rutebanken.extime.Constants.HEADER_MESSAGE_FILE_NAME;
import static no.rutebanken.extime.Constants.HEADER_MESSAGE_PROVIDER_ID;
import static no.rutebanken.extime.Constants.HEADER_MESSAGE_USERNAME;

/**
 * Tells marduk that a new NeTEx export is available in the marduk exchange bucket.
 *
 * <p>Replaces {@code direct:notifyMarduk} and the {@code interceptSendToEndpoint("google-pubsub:*")}
 * that turned every exchange header into a message attribute. That interceptor also shipped whatever
 * else happened to be on the exchange -- {@code FileNameGenerated}, {@code CamelFileNameProduced}, the
 * quartz trigger headers. Marduk reads none of those, so the attribute set is now stated explicitly and
 * pinned by {@code WireContractTest}.
 */
@Component
public class MardukNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(MardukNotifier.class);

    private final PubSubTemplate pubSubTemplate;
    private final RetrySettings retrySettings;
    private final String destinationName;
    private final String providerId;

    public MardukNotifier(
            PubSubTemplate pubSubTemplate,
            RetrySettings retrySettings,
            @Value("${queue.upload.destination.name}") String destinationName,
            @Value("${blobstore.provider.id}") String providerId) {
        this.pubSubTemplate = pubSubTemplate;
        this.retrySettings = retrySettings;
        this.destinationName = destinationName;
        this.providerId = providerId;
    }

    public void notifyMarduk(String fileName, String fileHandle, String correlationId) {
        LOGGER.info("Notifying marduk queue about NeTEx export");
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(HEADER_MESSAGE_CORRELATION_ID, correlationId);
        attributes.put(HEADER_MESSAGE_FILE_HANDLE, fileHandle);
        attributes.put(HEADER_MESSAGE_PROVIDER_ID, providerId);
        attributes.put(HEADER_MESSAGE_FILE_NAME, fileName);
        attributes.put(HEADER_MESSAGE_USERNAME, EXTIME_USERNAME);
        attributes.put(HEADER_LEGACY_CAMEL_FILE_NAME, fileName);

        Retry.withRetry(retrySettings, "Notifying marduk about " + fileName,
                () -> publishAndWait(attributes));
    }

    /**
     * Blocks on the publish future. The export is finished at this point, so a silently dropped
     * notification would leave a dataset in the bucket that marduk never imports.
     */
    private void publishAndWait(Map<String, String> attributes) throws InterruptedException {
        try {
            pubSubTemplate.publish(destinationName, "", attributes).get();
        } catch (ExecutionException e) {
            throw new ExtimeException("Failed to publish to " + destinationName, e.getCause());
        }
    }
}
