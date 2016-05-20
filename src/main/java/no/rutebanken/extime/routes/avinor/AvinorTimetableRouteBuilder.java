package no.rutebanken.extime.routes.avinor;

import no.rutebanken.extime.model.AirportFlightDataSet;
import no.rutebanken.extime.model.IATA;
import no.rutebanken.extime.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AvinorTimetableRouteBuilder extends BaseRouteBuilder {

    static final String HEADER_AIRPORT_IATA = "AirportIATA";

    @Override
    public void configure() throws Exception {
        super.configure();
        getContext().setTracing(true);

        from("{{avinor.timetable.scheduler.cron}}")
                .routeId("AvinorTimetableSchedulerStarter")
                .process(new AirportIATAProcessor()).id("AirportIATAProcessor")
                .split(body(), new AirportFlightAggregationStrategy()).parallelProcessing()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Processing airport: ${body}")
                    .setHeader(HEADER_AIRPORT_IATA, simple("${body}"))
                    .to("direct:fetchTimetableForAirport").id("FetchTimetableProcessor")
                .end()
                .to("mock:processAggregatedFlights")
        ;

        from("direct:fetchTimetableForAirport")
                .routeId("TimetableFetcher")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetching flights for ${body}")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .setHeader(Exchange.HTTP_QUERY, simpleF("airport=${header.%s}&timeFrom=0&timeTo=72", HEADER_AIRPORT_IATA))
                .setBody(constant(null))
                .doTry()
                    .to("http4://flydata.avinor.no/XmlFeed.asp").id("FetchTimetableProcessor")
                .doCatch(Exception.class)
                    .log(LoggingLevel.ERROR, this.getClass().getName(), "Could not connect to Avinor feed: ${exception}")
                .end()
        ;
    }

    class AirportIATAProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setBody(IATA.values());
        }
    }

    class AirportFlightAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            String airportIATACode = newExchange.getIn().getHeader(HEADER_AIRPORT_IATA, String.class);
            @SuppressWarnings("unchecked")
            AirportFlightDataSet newExchangeBody = newExchange.getIn().getBody(AirportFlightDataSet.class);
            if (oldExchange == null) {
                Map<String, AirportFlightDataSet> airportFlightsMap = new HashMap<>();
                airportFlightsMap.put(airportIATACode, newExchangeBody);
                newExchange.getIn().setBody(airportFlightsMap);
                return newExchange;
            } else {
                @SuppressWarnings("unchecked")
                Map<String, AirportFlightDataSet> airportFlightsMap = oldExchange.getIn().getBody(Map.class);
                airportFlightsMap.put(airportIATACode, newExchangeBody);
                return oldExchange;
            }
        }
    }
}
