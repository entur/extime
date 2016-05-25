package no.rutebanken.extime.routes.avinor;

import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.model.AirportIATA;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.springframework.stereotype.Component;

import static org.apache.camel.component.stax.StAXBuilder.stax;

@Component
public class AvinorRealTimeRouteBuilder extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        //super.configure();
        //getContext().setTracing(true);

        from("{{avinor.realtime.scheduler.cron}}")
                .routeId("AvinorRealTimeSchedulerStarter")
                .process(exchange -> {
                    exchange.getIn().setBody(AirportIATA.values());
                }).id("RealTimeAirportIATAProcessor")
                .split(body()).parallelProcessing()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetching flights for ${body}")
                    .setHeader("AirportIATASplit", simple("${body}"))
                    .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                    // fetch all departure flights from Oslo
                    .setHeader(Exchange.HTTP_QUERY, simpleF("airport=${header.%s}&direction=D&PeriodFrom=2016-05-25Z&PeriodTo=2016-05-25Z", "AirportIATASplit"))
                    .setBody(constant(null))
                    .to("{{avinor.realtime.feed.endpoint}}").id("FetchRealTimeFeedProcessor")
                .end()
        ;

        from("direct:unmarshalFlights")
                .routeId("AvinorRealTimeFlightUnmarshaller")
                .split(stax(Flight.class, false)).streaming()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetched flight with id: ${body.flightId}")
                .end()
        ;
    }
}
