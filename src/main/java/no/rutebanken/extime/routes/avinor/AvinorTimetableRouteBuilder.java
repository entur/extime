package no.rutebanken.extime.routes.avinor;

import com.google.common.base.Strings;
import no.avinor.flydata.xjc.model.airport.AirportNames;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.converter.ScheduledFlightConverter;
import no.rutebanken.extime.converter.ScheduledFlightToNetexConverter;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.ScheduledDirectFlight;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cache.CacheConstants;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.camel.component.stax.StAXBuilder.stax;

@Component
public class AvinorTimetableRouteBuilder extends RouteBuilder { //extends BaseRouteBuilder {

    static final String HEADER_TIMETABLE_AIRPORT_IATA = "TimetableAirportIATA";
    static final String HEADER_STOPVISIT_AIRPORT_IATA = "StopVisitAirportIATA";
    static final String HEADER_TIMETABLE_PERIOD_FROM = "TimetablePeriodFrom";
    static final String HEADER_TIMETABLE_PERIOD_TO = "TimetablePeriodTo";

    static final String PROPERTY_AIRPORT_NAME_ORIGINAL_BODY = "AirportNameOriginalBody";

    @Override
    public void configure() throws Exception {
        //super.configure();
        //getContext().setTracing(true);

        from("{{avinor.timetable.scheduler.cron}}")
                .routeId("AvinorTimetableSchedulerStarter")
                .process(exchange -> {exchange.getIn().setBody(AirportIATA.values());}).id("TimetableAirportIATAProcessor")
                .setHeader(HEADER_TIMETABLE_PERIOD_FROM, simple("${date:now:yyyy-MM-dd}Z"))
                .process(exchange -> {
                    Long daysAhead = simple("{{avinor.timetable.periodto.daysahead}}").evaluate(exchange, Long.class);
                    String periodTo = LocalDate.now().plusDays(daysAhead).toString().concat("Z");
                    exchange.getIn().setHeader(HEADER_TIMETABLE_PERIOD_TO, periodTo);
                }).id("TimetablePeriodToProcessor")
                .split(body(), new ScheduledFlightListAggregationStrategy()).parallelProcessing()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(),
                            "Processing scheduled flights for airport with IATA code: ${body}")
                    .setHeader(HEADER_TIMETABLE_AIRPORT_IATA, simple("${body}"))
                    .to("direct:fetchAirportNameByIATA").id("FetchAirportNameProcessor")
                    .to("direct:fetchTimetableForAirport").id("FetchTimetableProcessor")
                .end()
                .to("direct:convertTimetableForAirports")
        ;

        from("direct:fetchAirportNameByIATA")
                .routeId("FetchAirportNameByIata")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetching airport name by IATA: ${body}")
                .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_CHECK))
                .setHeader(CacheConstants.CACHE_KEY, simpleF("${header.%s}", HEADER_TIMETABLE_AIRPORT_IATA))
                .to("cache://avinorTimetableCache").id("TimetableCacheCheckProcessor")
                .choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNull())
                    .to("direct:fetchAirportNameFromFeed")
                    .to("direct:addAirportNameToCache")
                .end()
        ;

        from("direct:fetchAirportNameFromFeed")
                .routeId("FetchAirportNameFromFeed")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetching airport name from feed by IATA: ${header.TimetableAirportIATA}")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .setHeader(Exchange.HTTP_QUERY, simpleF("airport=${header.%s}&shortname=Y&ukname=Y", HEADER_TIMETABLE_AIRPORT_IATA))
                .setBody(constant(null))
                .to("{{avinor.airport.feed.endpoint}}").id("FetchAirportNameFeedProcessor")
                .convertBodyTo(AirportNames.class)
                .process(exchange -> {
                    AirportNames airportNames = exchange.getIn().getBody(AirportNames.class);
                    String name = airportNames.getAirportName().get(0).getName();
                    exchange.getIn().setBody(name, String.class);
                })
        ;

        from("direct:fetchTimetableForAirport")
                .routeId("FetchTimetableForAirport")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetching flights for airport IATA: ${header.TimetableAirportIATA}")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .setHeader(Exchange.HTTP_QUERY, simpleF("airport=${header.%s}&direction=D&PeriodFrom=${header.%s}&PeriodTo=${header.%s}",
                        HEADER_TIMETABLE_AIRPORT_IATA, HEADER_TIMETABLE_PERIOD_FROM, HEADER_TIMETABLE_PERIOD_TO))
                .setBody(constant(null))
                .to("{{avinor.timetable.feed.endpoint}}").id("FetchTimetableFeedProcessor")
                .split(stax(Flight.class, false), new ScheduledAirportFlightsAggregationStrategy()).streaming()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(),
                            "Fetched flight with id: ${body.airlineDesignator}${body.flightNumber}")
                .end()
        ;

        from("direct:convertTimetableForAirports")
                .routeId("TimetableConverter")
                .multicast()
                    .to("direct:convertToDirectFlights").id("ConvertDirectFlightsProcessor")
                    .to("mock:direct:convertToStopoverFlights").id("ConvertStopoverFlightsProcessor")
                .end()
        ;

        from("direct:convertToDirectFlights")
                .routeId("DirectFlightsConverter")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Converting to scheduled direct flights")
                .bean(ScheduledFlightConverter.class, "convertToScheduledDirectFlights")
                .to("direct:convertDirectFlightsToNetex")
        ;

        from("direct:convertToStopoverFlights")
                .routeId("StopoverFlightsConverter")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Converting to scheduled stopover flights")
                .bean(ScheduledFlightConverter.class, "convertToScheduledStopoverFlights")
                .to("direct:convertStopoverFlightsToNetex")
        ;

        from("direct:convertDirectFlightsToNetex")
                .routeId("DirectFlightsNetexConverter")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Converting scheduled direct flights to NeTEx")
                .split(body()).parallelProcessing()
                    .process(new DepartureIataInitProcessor())
                    .enrich("direct:retrieveAirportNameResource", new DepartureIataEnricherAggregationStrategy())
                    .process(new ArrivalIataInitProcessor())
                    .enrich("direct:retrieveAirportNameResource", new ArrivalIataEnricherAggregationStrategy())
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Converting scheduled direct flight with id: ${body.flightId}")
                    .bean(ScheduledFlightToNetexConverter.class, "convertToNetex")
                    .convertBodyTo(String.class)
                    //.log(LoggingLevel.DEBUG, this.getClass().getName(), "${body}")
                    //.to("mock:jms:queue")
                    .process(exchange -> {
                        String uuid = getContext().getUuidGenerator().generateUuid();
                        exchange.getIn().setHeader("FileNameGenerated", uuid);
                    })
                    .setHeader(Exchange.FILE_NAME, simple("${header.FileNameGenerated}.xml"))
                    .to("file:target/netex")
                .end()
        ;

        from("direct:convertStopoverFlightsToNetex")
                .routeId("StopoverFlightsNetexConverter")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Converting scheduled stopover flights to NeTEx")
                .split(body()).parallelProcessing()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Converting scheduled stopover flight with id: ${body.flightId}")
                    .bean(ScheduledFlightToNetexConverter.class, "convertToNetex")
                    .to("mock:jms:queue")
                .end()
        ;

        from("direct:retrieveAirportNameResource")
                .routeId("TimetableAirportNameEnricher")
                .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_GET))
                .setHeader(CacheConstants.CACHE_KEY, simple("${body}"))
                .to("cache://avinorTimetableCache")
/*
                .choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNull())
                    .setHeader(HEADER_TIMETABLE_AIRPORT_IATA, simple("${body.departureAirportIATA}"))
                    .to("direct:fetchAirportNameFromFeed")
                    .to("direct:addAirportNameToCache")
                .end()
*/
        ;

        from("direct:getAirportNameFromCache")
                .routeId("AirportNameGetFromCache")
                .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_GET))
                .setHeader(CacheConstants.CACHE_KEY, simpleF("${header.%s}", HEADER_STOPVISIT_AIRPORT_IATA))
                .to("cache://avinorTimetableCache")
        ;

        from("direct:addAirportNameToCache")
                .routeId("AirportNameAddToCache")
                .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD))
                .setHeader(CacheConstants.CACHE_KEY, simpleF("${header.%s}", HEADER_TIMETABLE_AIRPORT_IATA))
                .log(LoggingLevel.DEBUG, this.getClass().getName(), String.format("ABOUT TO CACHE: ${header.%s}:${body}", CacheConstants.CACHE_KEY))
                .to("cache://avinorTimetableCache").id("TimetableCacheAddProcessor")
        ;

        from("cache://avinorTimetableCache" +
                "?maxElementsInMemory=50" +
                "&eternal=true" +
                "&timeToLiveSeconds=300" +
                "&overflowToDisk=true" +
                "&diskPersistent=true")
                .routeId("AvinorTimetableCache")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "${header.TimetableAirportIATA}:${body}")
        ;
    }

    @SuppressWarnings("Duplicates")
    class ScheduledFlightListAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            @SuppressWarnings("unchecked")
            List<Flight> newExchangeBody = newExchange.getIn().getBody(List.class);
            if (oldExchange == null) {
                List<Flight> scheduledFlights = new ArrayList<>();
                scheduledFlights.addAll(newExchangeBody);
                newExchange.getIn().setBody(scheduledFlights);
                return newExchange;
            } else {
                @SuppressWarnings("unchecked")
                List<Flight> scheduledFlights = Collections.checkedList(
                        oldExchange.getIn().getBody(List.class), Flight.class);
                scheduledFlights.addAll(newExchangeBody);
                return oldExchange;
            }
        }
    }

    @SuppressWarnings("Duplicates")
    class ScheduledAirportFlightsAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            Flight newFlight = newExchange.getIn().getBody(Flight.class);
            if (oldExchange == null) {
                List<Flight> flightList = new ArrayList<>();
                if (isDomesticFlight(newFlight)) {
                    flightList.add(newFlight);
                }
                newExchange.getIn().setBody(flightList);
                return newExchange;
            } else {
                @SuppressWarnings("unchecked")
                List<Flight> flightList = Collections.checkedList(
                        oldExchange.getIn().getBody(List.class), Flight.class);
                if (isDomesticFlight(newFlight)) {
                    flightList.add(newFlight);
                }
                return oldExchange;
            }
        }

        private boolean isValidFlight(Flight flight) {
            return flight.getId() != null &&
                    !Strings.isNullOrEmpty(flight.getAirlineDesignator()) &&
                    !Strings.isNullOrEmpty(flight.getFlightNumber()) &&
                    flight.getDateOfOperation() != null &&
                    !Strings.isNullOrEmpty(flight.getDepartureStation()) &&
                    flight.getStd() != null &&
                    !Strings.isNullOrEmpty(flight.getArrivalStation()) &&
                    flight.getSta() != null &&
                    isDomesticFlight(flight);
        }

        private boolean isDomesticFlight(Flight flight) {
            return isValidDepartureAndArrival(flight.getDepartureStation(), flight.getArrivalStation());
        }

        private boolean isValidDepartureAndArrival(String departureIATA, String arrivalIATA) {
            return EnumUtils.isValidEnum(AirportIATA.class, departureIATA)
                    && EnumUtils.isValidEnum(AirportIATA.class, arrivalIATA);
        }
    }

    class DepartureIataInitProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            ScheduledDirectFlight originalBody = exchange.getIn().getBody(ScheduledDirectFlight.class);
            exchange.setProperty(PROPERTY_AIRPORT_NAME_ORIGINAL_BODY, originalBody);
            String enrichParameter = originalBody.getDepartureAirportIATA();
            exchange.getIn().setBody(enrichParameter);
        }
    }

    class ArrivalIataInitProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            ScheduledDirectFlight originalBody = exchange.getIn().getBody(ScheduledDirectFlight.class);
            exchange.setProperty(PROPERTY_AIRPORT_NAME_ORIGINAL_BODY, originalBody);
            String enrichParameter = originalBody.getArrivalAirportIATA();
            exchange.getIn().setBody(enrichParameter);
        }
    }

    class DepartureIataEnricherAggregationStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange original, Exchange resource) {
            ScheduledDirectFlight originalBody = original.getProperty(
                    PROPERTY_AIRPORT_NAME_ORIGINAL_BODY, ScheduledDirectFlight.class);
            String resourceResponse = resource.getIn().getBody(String.class);
            originalBody.setDepartureAirportName(resourceResponse);
            original.getIn().setBody(originalBody);
            return original;
        }
    }

    class ArrivalIataEnricherAggregationStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange original, Exchange resource) {
            ScheduledDirectFlight originalBody = original.getProperty(
                    PROPERTY_AIRPORT_NAME_ORIGINAL_BODY, ScheduledDirectFlight.class);
            String resourceResponse = resource.getIn().getBody(String.class);
            originalBody.setArrivalAirportName(resourceResponse);
            original.getIn().setBody(originalBody);
            return original;
        }
    }

}
