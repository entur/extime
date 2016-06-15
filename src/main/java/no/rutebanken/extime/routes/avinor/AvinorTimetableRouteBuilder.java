package no.rutebanken.extime.routes.avinor;

import com.google.common.collect.Lists;
import no.avinor.flydata.xjc.model.airport.AirportNames;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.converter.ScheduledFlightConverter;
import no.rutebanken.extime.converter.ScheduledFlightToNetexConverter;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.ScheduledDirectFlight;
import no.rutebanken.extime.model.ScheduledStopover;
import no.rutebanken.extime.model.ScheduledStopoverFlight;
import no.rutebanken.extime.util.DateUtils;
import no.rutebanken.netex.model.PublicationDeliveryStructure;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cache.CacheConstants;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.camel.component.stax.StAXBuilder.stax;

@Component
public class AvinorTimetableRouteBuilder extends RouteBuilder { //extends BaseRouteBuilder {

    public static final String HEADER_TIMETABLE_AIRPORT_IATA = "TimetableAirportIATA";
    public static final String HEADER_TIMETABLE_SMALL_AIRPORT_RANGE = "TimetableSmallAirportRange";
    public static final String HEADER_TIMETABLE_MEDIUM_AIRPORT_RANGE = "TimetableMediumAirportRange";
    public static final String HEADER_TIMETABLE_LARGE_AIRPORT_RANGE = "TimetableLargeAirportRange";

    static final String PROPERTY_DIRECT_FLIGHT_ORIGINAL_BODY = "DirectFlightOriginalBody";
    static final String PROPERTY_STOPOVER_FLIGHT_ORIGINAL_BODY = "StopoverFlightOriginalBody";
    static final String PROPERTY_STOPOVER_ORIGINAL_BODY = "StopoverOriginalBody";

    @Override
    public void configure() throws Exception {
        //super.configure();
        //getContext().setTracing(true);

        JaxbDataFormat jaxbDataFormat = new JaxbDataFormat();
        //JaxbDataFormat jaxbDataFormat = new JaxbDataFormat("no.rutebanken.netex.model"); // same as below
        jaxbDataFormat.setContextPath(PublicationDeliveryStructure.class.getPackage().getName());
        //jaxbDataFormat.setSchema("classpath:person.xsd,classpath:address.xsd"); // multiple xsds
        //jaxbDataFormat.setSchema("classpath:person.xsd"); // single xsd
        //jaxbDataFormat.setSchema("https://raw.githubusercontent.com/rutebanken/NeTEx-XML/master/schema/1.03/xsd/NeTEx_publication.xsd"); // single xsd
        jaxbDataFormat.setPrettyPrint(true);
        jaxbDataFormat.setEncoding("iso-8859-1");

        from("{{avinor.timetable.scheduler.consumer}}")
                .routeId("AvinorTimetableSchedulerStarter")
                .process(exchange -> {exchange.getIn().setBody(AirportIATA.values());}).id("TimetableAirportIATAProcessor")
                .bean(DateUtils.class, "generateDateRanges").id("TimetableDateRangeProcessor")
                .split(body(), new ScheduledFlightListAggregationStrategy()).parallelProcessing()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Processing airport with IATA code: ${body}")
                    .setHeader(HEADER_TIMETABLE_AIRPORT_IATA, simple("${body}"))
                    .to("direct:fetchAirportNameByIATA").id("FetchAirportNameProcessor")
                    .choice()
                        .when(simpleF("${header.%s} in ${properties:avinor.airports.large}", HEADER_TIMETABLE_AIRPORT_IATA))
                            .log(LoggingLevel.DEBUG, this.getClass().getName(),
                                    "Configuring date ranges for large size airport: ${body}").id("LargeAirportLogProcessor")
                            .setBody(simpleF("${header.%s}", HEADER_TIMETABLE_LARGE_AIRPORT_RANGE))
                        .when(simpleF("${header.%s} in ${properties:avinor.airports.medium}", HEADER_TIMETABLE_AIRPORT_IATA))
                            .log(LoggingLevel.DEBUG, this.getClass().getName(),
                                    "Configuring date ranges for medium size airport: ${body}").id("MediumAirportLogProcessor")
                            .setBody(simpleF("${header.%s}", HEADER_TIMETABLE_MEDIUM_AIRPORT_RANGE))
                        .otherwise()
                            .log(LoggingLevel.DEBUG, this.getClass().getName(),
                                    "Configuring date ranges for small size airport: ${body}").id("SmallAirportLogProcessor")
                            .setBody(simpleF("${header.%s}", HEADER_TIMETABLE_SMALL_AIRPORT_RANGE))
                    .end()
                    .to("direct:fetchTimetableForAirportByRanges").id("FetchTimetableProcessor")
                .end()
                .to("direct:convertTimetableForAirports")
        ;

        from("direct:fetchAirportNameByIATA")
                .routeId("FetchAirportNameByIata")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetching airport name by IATA: ${header.TimetableAirportIATA}")
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
                .process(new ExtractAirportNameProcessor())
        ;

        from("direct:fetchTimetableForAirportByRanges")
                .routeId("FetchTimetableForAirportByDateRanges")
                .split(body(), new ScheduledFlightListAggregationStrategy())
                    .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                    .setHeader(Exchange.HTTP_QUERY,
                            simpleF("airport=${header.%s}&direction=D&PeriodFrom=${body.lowerEndpoint()}Z&PeriodTo=${body.upperEndpoint()}Z",
                                    HEADER_TIMETABLE_AIRPORT_IATA))
                    .setBody(constant(null))
                    .log(LoggingLevel.DEBUG, this.getClass().getName(),
                            "Fetching flights for ${header.TimetableAirportIATA} by date range: ${body.lowerEndpoint()} - ${body.upperEndpoint()}")
                    .to("{{avinor.timetable.feed.endpoint}}").id("FetchTimetableFeedByRangesProcessor")
                    .to("direct:splitJoinIncomingFlightMessages").id("SplitJoinIncomingFlightsProcessor")
                .end()
        ;

        from("direct:splitJoinIncomingFlightMessages")
                .routeId("FlightSplitterJoiner")
                .split(stax(Flight.class, false), new ScheduledAirportFlightsAggregationStrategy()).streaming()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetched flight with id: ${body.airlineDesignator}${body.flightNumber}")
                        .id("FlightSplitLogProcessor")
                .end()
        ;

        from("direct:convertTimetableForAirports")
                .routeId("TimetableConverter")
                .multicast()
                    .to("mock:direct:convertToDirectFlights").id("ConvertDirectFlightsProcessor")
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
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Converting scheduled direct flight with id: ${body.airlineFlightId}")
                    .bean(ScheduledFlightToNetexConverter.class, "convertToNetex")
                    //.setHeader(Exchange.CHARSET_NAME, constant("iso-8859-1"))
                    .marshal(jaxbDataFormat)
                    //.log(LoggingLevel.DEBUG, this.getClass().getName(), "${body}")
                    .process(exchange -> {
                        String uuid = getContext().getUuidGenerator().generateUuid();
                        exchange.getIn().setHeader("FileNameGenerated", uuid);
                    })
                    .setHeader(Exchange.FILE_NAME, simple("${header.FileNameGenerated}.xml"))
                    .to("mock:file:target/netex")
                .end()
        ;

        from("direct:convertStopoverFlightsToNetex")
                .routeId("StopoverFlightsNetexConverter")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Converting scheduled stopover flights to NeTEx")
                .split(body()).parallelProcessing()
                    .process(new StopverIataInitProcessor())
                    .enrich("direct:retrieveAirportNamesForStopovers", new StopoverIataEnricherAggregationStrategy())
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Converting scheduled stopover flight with id: ${body.flightId}")
                    .bean(ScheduledFlightToNetexConverter.class, "convertToNetex")
                    .marshal(jaxbDataFormat)
                    .process(exchange -> {
                        String uuid = getContext().getUuidGenerator().generateUuid();
                        exchange.getIn().setHeader("StopoverFileName", uuid);
                    })
                    .setHeader(Exchange.FILE_NAME, simple("${header.StopoverFileName}.xml"))
                    .to("file:target/netex/stopover")
                .end()
        ;

        /**
         * @todo: add support for fetching and caching airport names when not found in cache (remove block comment!)
         */
        from("direct:retrieveAirportNameResource")
                .routeId("DirectFlightAirportNameEnricher")
                .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_GET))
                .setHeader(CacheConstants.CACHE_KEY, simple("${body}"))
                .to("cache://avinorTimetableCache").id("CacheGetAirportNameProcessor")
/*
                .choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNull())
                    .setHeader(HEADER_TIMETABLE_AIRPORT_IATA, simple("${body.departureAirportIATA}"))
                    .to("direct:fetchAirportNameFromFeed")
                    .to("direct:addAirportNameToCache")
                .end()
*/
        ;

        from("direct:retrieveAirportNamesForStopovers")
                .routeId("StopoverFlightAirportNameEnricher")
                .split(body(), new StopoverListAggregationStrategy())
                    .setProperty(PROPERTY_STOPOVER_ORIGINAL_BODY, simple("${body}"))
                    .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_GET))
                    .setHeader(CacheConstants.CACHE_KEY, simple("${body.airportIATA}"))
                    .to("cache://avinorTimetableCache").id("CacheGetAirportNameForStopoverProcessor")
                .end()
        ;

        from("direct:addAirportNameToCache")
                .routeId("AirportNameAddToCache")
                .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD))
                .setHeader(CacheConstants.CACHE_KEY, simpleF("${header.%s}", HEADER_TIMETABLE_AIRPORT_IATA))
                .log(LoggingLevel.DEBUG, this.getClass().getName(), String.format("Adding to cache: ${header.%s}:${body}", CacheConstants.CACHE_KEY))
                .to("cache://avinorTimetableCache").id("TimetableCacheAddProcessor")
        ;

        from("cache://avinorTimetableCache" +
                "?maxElementsInMemory=50" +
                "&eternal=true" +
                "&timeToLiveSeconds=300" +
                "&overflowToDisk=true" +
                "&diskPersistent=true")
                .routeId("AvinorTimetableCache")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "${header.CamelCacheKey}:${body}")
        ;
    }

    class ExtractAirportNameProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            AirportNames airportNames = exchange.getIn().getBody(AirportNames.class);
            String name = airportNames.getAirportName().get(0).getName();
            exchange.getIn().setBody(name, String.class);
        }
    }

    class ScheduledFlightListAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            @SuppressWarnings("unchecked")
            List<Flight> newExchangeBody = newExchange.getIn().getBody(List.class);
            if (oldExchange == null) {
                List<Flight> scheduledFlights = Lists.newArrayList();
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

        private boolean isDomesticFlight(Flight flight) {
            return isValidDepartureAndArrival(flight.getDepartureStation(), flight.getArrivalStation());
        }

        private boolean isValidDepartureAndArrival(String departureIATA, String arrivalIATA) {
            return EnumUtils.isValidEnum(AirportIATA.class, departureIATA)
                    && EnumUtils.isValidEnum(AirportIATA.class, arrivalIATA);
        }
    }

    class StopoverListAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            @SuppressWarnings("unchecked")
            String newExchangeBody = newExchange.getIn().getBody(String.class);
            if (oldExchange == null) {
                List<ScheduledStopover> scheduledStopovers = new ArrayList<>();
                ScheduledStopover originalBody = newExchange.getProperty(
                        PROPERTY_STOPOVER_ORIGINAL_BODY, ScheduledStopover.class);
                originalBody.setAirportName(newExchangeBody);
                scheduledStopovers.add(originalBody);
                newExchange.getIn().setBody(scheduledStopovers);
                return newExchange;
            } else {
                @SuppressWarnings("unchecked")
                List<ScheduledStopover> scheduledStopovers = Collections.checkedList(
                        oldExchange.getIn().getBody(List.class), ScheduledStopover.class);
                ScheduledStopover originalBody = newExchange.getProperty(
                        PROPERTY_STOPOVER_ORIGINAL_BODY, ScheduledStopover.class);
                originalBody.setAirportName(newExchangeBody);
                scheduledStopovers.add(originalBody);
                return oldExchange;
            }
        }
    }

    class DepartureIataInitProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            ScheduledDirectFlight originalBody = exchange.getIn().getBody(ScheduledDirectFlight.class);
            exchange.setProperty(PROPERTY_DIRECT_FLIGHT_ORIGINAL_BODY, originalBody);
            String enrichParameter = originalBody.getDepartureAirportIATA();
            exchange.getIn().setBody(enrichParameter);
        }
    }

    class ArrivalIataInitProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            ScheduledDirectFlight originalBody = exchange.getIn().getBody(ScheduledDirectFlight.class);
            exchange.setProperty(PROPERTY_DIRECT_FLIGHT_ORIGINAL_BODY, originalBody);
            String enrichParameter = originalBody.getArrivalAirportIATA();
            exchange.getIn().setBody(enrichParameter);
        }
    }

    class StopverIataInitProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            ScheduledStopoverFlight originalBody = exchange.getIn().getBody(ScheduledStopoverFlight.class);
            exchange.setProperty(PROPERTY_STOPOVER_FLIGHT_ORIGINAL_BODY, originalBody);
            List<ScheduledStopover> enrichParameter = originalBody.getScheduledStopovers();
            exchange.getIn().setBody(enrichParameter);
        }
    }

    class DepartureIataEnricherAggregationStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange original, Exchange resource) {
            ScheduledDirectFlight originalBody = original.getProperty(
                    PROPERTY_DIRECT_FLIGHT_ORIGINAL_BODY, ScheduledDirectFlight.class);
            String resourceResponse = resource.getIn().getBody(String.class);
            originalBody.setDepartureAirportName(resourceResponse);
            original.getIn().setBody(originalBody);
            return original;
        }
    }

    class ArrivalIataEnricherAggregationStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange original, Exchange resource) {
            ScheduledDirectFlight originalBody = original.getProperty(
                    PROPERTY_DIRECT_FLIGHT_ORIGINAL_BODY, ScheduledDirectFlight.class);
            String resourceResponse = resource.getIn().getBody(String.class);
            originalBody.setArrivalAirportName(resourceResponse);
            original.getIn().setBody(originalBody);
            return original;
        }
    }

    class StopoverIataEnricherAggregationStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange original, Exchange resource) {
            ScheduledStopoverFlight originalBody = original.getProperty(
                    PROPERTY_STOPOVER_FLIGHT_ORIGINAL_BODY, ScheduledStopoverFlight.class);
            List<ScheduledStopover> resourceResponse = resource.getIn().getBody(List.class);
            originalBody.getScheduledStopovers().clear();
            originalBody.getScheduledStopovers().addAll(resourceResponse);
            original.getIn().setBody(originalBody);
            return original;
        }
    }

}
