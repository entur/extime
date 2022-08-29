package no.rutebanken.extime.routes.avinor;

import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.StopVisitType;
import no.rutebanken.extime.routes.BaseRouteBuilder;
import no.rutebanken.extime.util.DateUtils;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpMethods;

import java.util.Arrays;

import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_HTTP_URI;

//@Component
public class FlightDataProducerRouteBuilder extends BaseRouteBuilder {

    @Override
    public void configure() {

        super.configure();

        from("quartz://flightDataScheduler?trigger.repeatCount=0")
                .routeId("FlightDataProducerStarter")
                .autoStartup(false)
                .process(exchange -> {
                    AirportIATA[] airportIATAs = Arrays.stream(AirportIATA.values())
                            .filter(iata -> !iata.equals(AirportIATA.OSL))
                            .toArray(AirportIATA[]::new);
                    exchange.getIn().setBody(airportIATAs);
                })
                .bean(DateUtils.class, "generateDateRanges")
                .split(body())
                    .setHeader("FlightIataCode", simple("${body}"))
                    .to("direct:fetchTimetableFromFeed")
                .end()
        ;

        from("direct:fetchTimetableFromFeed")
                .routeId("FetchTimetableFromFeed")
                .choice()
                    .when(simpleF("${header.%s} in ${properties:avinor.airports.large}", "FlightIataCode"))
                        .setBody(simpleF("${header.%s}", "TimetableLargeAirportRange"))
                        .to("direct:fetchTimetableByRanges")
                    .otherwise()
                        .setBody(simpleF("${header.%s}", "TimetableSmallAirportRange"))
                        .to("direct:fetchTimetableByRanges")
                .end()
        ;

        from("direct:fetchTimetableByRanges")
                .routeId("FetchTimetableByRanges")
                .setHeader(HEADER_EXTIME_HTTP_URI, simple("{{avinor.timetable.feed.endpoint}}"))
                .split(body())
                    .setHeader("DataFeedLowerRange", simple("${bean:dateUtils.format(${body.lowerEndpoint()})}Z"))
                    .setHeader("DataFeedUpperRange", simple("${bean:dateUtils.format(${body.upperEndpoint()})}Z"))
                    .to("direct:fetchFlightsFromFeed")
                .end()
        ;

        from("direct:fetchFlightsFromFeed")
                .routeId("FetchFlightsFromFeed")
                .process(exchange -> exchange.getIn().setBody(StopVisitType.values()))
                .split(body())
                    .setHeader("CurrentStopVisitType", body())
                    .setHeader("FeedUriParameters", simpleF("airport=${header.%s}&direction=${body.code}&PeriodFrom=${header.%s}&PeriodTo=${header.%s}",
                            "FlightIataCode", "DataFeedLowerRange", "DataFeedUpperRange"))
                    .to("direct:fetchFromHttpFeed")
                .end()
        ;

        from("direct:fetchFromHttpFeed")
                .routeId("FetchFromHttpFeedEndpoint")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .setHeader(Exchange.HTTP_QUERY, simpleF("${header.%s}", "FeedUriParameters"))
                .setBody(constant(""))
                .toD("${header." + HEADER_EXTIME_HTTP_URI + "}")
                // convert using the charset retrieved from the HTTP response header that Camel sets in the property Exchange.CHARSET_NAME
                .convertBodyTo(String.class)
                // remove the property Exchange.CHARSET_NAME after the conversion so that the JAXB formats can be overriden with a custom encoding
                .removeProperty(Exchange.CHARSET_NAME)
                .process(exchange -> {
                    String uuid = getContext().getUuidGenerator().generateUuid();
                    exchange.getIn().setHeader("FileNameGenerated", uuid);
                })
                .setHeader(Exchange.FILE_NAME, simple("${header.FileNameGenerated}.xml"))
                .to("file:target/testdata")
        ;

    }
}
