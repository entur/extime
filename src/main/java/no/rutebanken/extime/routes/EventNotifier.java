package no.rutebanken.extime.routes;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.impl.engine.DefaultInterceptSendToEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.entur.pubsub.base.EnturGooglePubSubAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EventNotifier extends EventNotifierSupport {

    @Autowired
    EnturGooglePubSubAdmin enturGooglePubSubAdmin;


    @Override
    public void notify(CamelEvent event) {

        if (event instanceof CamelEvent.CamelContextStartingEvent) {
            CamelContext context = ((CamelEvent.CamelContextStartingEvent) event).getContext();
            context.getEndpoints().stream().filter(e -> e.getEndpointUri().startsWith("google-pubsub")).forEach(e -> createSubscriptionIfMissing((e)));
        }

    }

    private void createSubscriptionIfMissing(Endpoint e) {
        GooglePubsubEndpoint gep;
        if (e instanceof GooglePubsubEndpoint) {
            gep = (GooglePubsubEndpoint) e;
        } else if (e instanceof DefaultInterceptSendToEndpoint) {
            gep = (GooglePubsubEndpoint) ((DefaultInterceptSendToEndpoint) e).getOriginalEndpoint();
        } else {
            throw new IllegalStateException("Incompatible endpoint: " + e);
        }
        enturGooglePubSubAdmin.createSubscriptionIfMissing(gep.getDestinationName());
    }

}