package no.rutebanken.extime.pubsub;

import org.entur.pubsub.base.EnturGooglePubSubAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates the PubSub destination extime publishes to, for unit tests and local development.
 *
 * <p>Replaces {@code AutoCreatePubSubSubscriptionEventNotifier}, which walked the CamelContext's
 * endpoints. Extime consumes nothing from PubSub, so a consumer-driven replacement would create nothing
 * at all and the first publish against a fresh emulator would fail with {@code NOT_FOUND}.
 *
 * <p>Still behind the {@code google-pubsub-autocreate} profile rather than
 * {@code entur.pubsub.subscriber.autocreate}, which defaults to true: extime's deployed service account
 * only holds {@code roles/pubsub.publisher} on marduk's project, so an unguarded {@code createTopic}
 * would fail the boot.
 */
@Component
@Profile("google-pubsub-autocreate")
public class PubSubPublishTargets {

    private static final Logger LOGGER = LoggerFactory.getLogger(PubSubPublishTargets.class);

    private final EnturGooglePubSubAdmin enturGooglePubSubAdmin;
    private final String destinationName;

    public PubSubPublishTargets(
            EnturGooglePubSubAdmin enturGooglePubSubAdmin,
            @Value("${queue.upload.destination.name}") String destinationName) {
        this.enturGooglePubSubAdmin = enturGooglePubSubAdmin;
        this.destinationName = destinationName;
    }

    @EventListener
    void handleContextRefreshed(ContextRefreshedEvent contextRefreshedEvent) {
        LOGGER.info("Creating PubSub destination {} if missing", destinationName);
        enturGooglePubSubAdmin.createSubscriptionIfMissing(destinationName);
    }
}
