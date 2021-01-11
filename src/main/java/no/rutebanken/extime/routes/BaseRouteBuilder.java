package no.rutebanken.extime.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends RouteBuilder {


    @Value("${extime.camel.redelivery.max:3}")
    private int maxRedelivery;

    @Value("${extime.camel.redelivery.delay:5000}")
    private int redeliveryDelay;

    @Value("${extime.camel.redelivery.backoff.multiplier:3}")
    private int backOffMultiplier;

    @Override
    public void configure() {
        errorHandler(defaultErrorHandler()
                .redeliveryDelay(redeliveryDelay)
                .maximumRedeliveries(maxRedelivery)
                .onRedelivery(this::logRedelivery)
                .useExponentialBackOff()
                .backOffMultiplier(backOffMultiplier)
                .logExhausted(true)
                .logRetryStackTrace(true));



        interceptFrom("google-pubsub:*")
                .process(exchange ->
                {
                    Map<String, String> pubSubAttributes = exchange.getIn().getHeader(GooglePubsubConstants.ATTRIBUTES, Map.class);
                    pubSubAttributes.entrySet().stream().filter(entry -> !entry.getKey().startsWith("CamelGooglePubsub")).forEach(entry -> exchange.getIn().setHeader(entry.getKey(), entry.getValue()));
                });

        interceptSendToEndpoint("google-pubsub:*").process(
                exchange -> {
                    Map<String, String> pubSubAttributes = new HashMap<>();
                    exchange.getIn().getHeaders().entrySet().stream()
                            .filter(entry -> !entry.getKey().startsWith("CamelGooglePubsub"))
                            .filter(entry -> Objects.toString(entry.getValue()).length() <= 1024)
                            .forEach(entry -> pubSubAttributes.put(entry.getKey(), Objects.toString(entry.getValue(), "")));
                    exchange.getIn().setHeader(GooglePubsubConstants.ATTRIBUTES, pubSubAttributes);

                });

    }

    protected void logRedelivery(Exchange exchange) {
        int redeliveryCounter = exchange.getIn().getHeader("CamelRedeliveryCounter", Integer.class);
        int redeliveryMaxCounter = exchange.getIn().getHeader("CamelRedeliveryMaxCounter", Integer.class);
        Throwable camelCaughtThrowable = exchange.getProperty("CamelExceptionCaught", Throwable.class);
        Throwable rootCause = ExceptionUtils.getRootCause(camelCaughtThrowable);

        String rootCauseType = rootCause != null ? rootCause.getClass().getName() : "";
        String rootCauseMessage = rootCause != null ? rootCause.getMessage() : "";

        log.warn("Exchange failed ({}: {}) . Redelivering the message locally, attempt {}/{}...", rootCauseType, rootCauseMessage, redeliveryCounter, redeliveryMaxCounter);
    }


}
