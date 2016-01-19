package no.rutebanken.extime.routes;

import java.net.ConnectException;

import org.apache.camel.builder.RouteBuilder;


/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends RouteBuilder {

   
    @Override
    public void configure() throws Exception {
        errorHandler(deadLetterChannel("activemq:queue:DeadLetterQueue")
            .maximumRedeliveries(3)
            .redeliveryDelay(3000));

        onException(ConnectException.class)
            .maximumRedeliveries(10)
                .redeliveryDelay(10000)
                .useExponentialBackOff();

    }

}
