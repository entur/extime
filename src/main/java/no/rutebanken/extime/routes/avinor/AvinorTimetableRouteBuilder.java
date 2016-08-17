package no.rutebanken.extime.routes.avinor;

import com.google.common.collect.Lists;
import no.avinor.flydata.xjc.model.airport.AirportNames;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.converter.ScheduledFlightConverter;
import no.rutebanken.extime.converter.ScheduledFlightToNetexConverter;
import no.rutebanken.extime.model.*;
import no.rutebanken.extime.util.AvinorTimetableUtils;
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

import java.util.*;

import static org.apache.camel.component.stax.StAXBuilder.stax;

@Component
public class AvinorTimetableRouteBuilder extends RouteBuilder { //extends BaseRouteBuilder {

    public static final String HEADER_EXTIME_HTTP_URI = "ExtimeHttpUri";
    public static final String HEADER_EXTIME_URI_PARAMETERS = "ExtimeUriParameters";
    public static final String HEADER_TIMETABLE_AIRPORT_IATA = "TimetableAirportIATA";
    public static final String HEADER_TIMETABLE_AIRLINE_IATA = "TimetableAirlineIATA";
    public static final String HEADER_TIMETABLE_SMALL_AIRPORT_RANGE = "TimetableSmallAirportRange";
    public static final String HEADER_TIMETABLE_LARGE_AIRPORT_RANGE = "TimetableLargeAirportRange";
    public static final String HEADER_TIMETABLE_STOP_VISIT_TYPE = "TimetableStopVisitType";
    public static final String HEADER_LOWER_RANGE_ENDPOINT = "LowerRangeEndpoint";
    public static final String HEADER_UPPER_RANGE_ENDPOINT = "UpperRangeEndpoint";

    static final String PROPERTY_SCHEDULED_FLIGHT_ORIGINAL_BODY = "ScheduledFlightOriginalBody";
    static final String PROPERTY_DIRECT_FLIGHT_ORIGINAL_BODY = "DirectFlightOriginalBody";
    static final String PROPERTY_STOPOVER_FLIGHT_ORIGINAL_BODY = "StopoverFlightOriginalBody";
    static final String PROPERTY_STOPOVER_ORIGINAL_BODY = "StopoverOriginalBody";

    @Override
    public void configure() throws Exception {
        //super.configure();
        //getContext().setTracing(true);

        JaxbDataFormat jaxbDataFormat = new JaxbDataFormat();
        jaxbDataFormat.setContextPath(PublicationDeliveryStructure.class.getPackage().getName());
        jaxbDataFormat.setSchema("classpath:/xsd/NeTEx-XML-1.04beta/schema/xsd/NeTEx_publication.xsd");
        jaxbDataFormat.setPrettyPrint(true);
        jaxbDataFormat.setEncoding("iso-8859-1");

        // @todo: enable parallell processing when going into test/beta/prod
        from("{{avinor.timetable.scheduler.consumer}}")
                .routeId("AvinorTimetableSchedulerStarter")
                .process(new AirportIataProcessor()).id("TimetableAirportIATAProcessor")
                .bean(DateUtils.class, "generateDateRanges").id("TimetableDateRangeProcessor")
                .split(body(), new ScheduledFlightListAggregationStrategy())//.parallelProcessing()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "==========================================")
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Processing airport with IATA code: ${body}")
                    .setHeader(HEADER_TIMETABLE_AIRPORT_IATA, simple("${body}"))
                    .to("direct:fetchAirportNameByIATA").id("FetchAirportNameProcessor")
                    .to("direct:fetchTimetableForAirport").id("FetchTimetableProcessor")
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Flights fetched for ${header.TimetableAirportIATA}")
                .end()
                .to("direct:convertToScheduledFlights").id("ConvertToScheduledFlightsProcessor")
                .multicast()
                    .to("mock:direct:convertToDirectFlights").id("ConvertDirectFlightsProcessor")
                    .to("mock:direct:convertToStopoverFlights").id("ConvertStopoverFlightsProcessor")
                .end()
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

        from("direct:fetchTimetableForAirport")
                .routeId("FetchTimetableForAirport")
                .choice()
                    .when(simpleF("${header.%s} in ${properties:avinor.airports.large}", HEADER_TIMETABLE_AIRPORT_IATA))
                        .log(LoggingLevel.DEBUG, this.getClass().getName(), "Configuring date ranges for large airport: ${body}")
                            .id("LargeAirportLogProcessor")
                        .setBody(simpleF("${header.%s}", HEADER_TIMETABLE_LARGE_AIRPORT_RANGE))
                        .to("direct:fetchTimetableForAirportByRanges")
                    .otherwise()
                        .log(LoggingLevel.DEBUG, this.getClass().getName(), "Configuring date ranges for small airport: ${body}")
                            .id("SmallAirportLogProcessor")
                        .setBody(simpleF("${header.%s}", HEADER_TIMETABLE_SMALL_AIRPORT_RANGE))
                        .to("direct:fetchTimetableForAirportByRanges")
                .end()
        ;

        from("direct:fetchAirportNameFromFeed")
                .routeId("FetchAirportNameFromFeed")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetching airport name from feed by IATA: ${header.TimetableAirportIATA}")
                .setHeader(HEADER_EXTIME_HTTP_URI, simple("{{avinor.airport.feed.endpoint}}"))
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "EXTIME HTTP URL SET TO: ${header.ExtimeHttpUri}")
                .setHeader(HEADER_EXTIME_URI_PARAMETERS, simpleF("airport=${header.%s}&shortname=Y&ukname=Y", HEADER_TIMETABLE_AIRPORT_IATA))
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "URI PARAMETERS SET TO: ${header.ExtimeUriParameters}")
                .to("direct:fetchFromHttpResource").id("FetchAirportNameFromHttpResourceProcessor")
                .convertBodyTo(AirportNames.class)
                .process(new ExtractAirportNameProcessor())
        ;

        from("direct:fetchTimetableForAirportByRanges")
                .routeId("FetchTimetableForAirportByDateRanges")
                .setHeader(HEADER_EXTIME_HTTP_URI, simple("{{avinor.timetable.feed.endpoint}}"))
                .split(body(), new ScheduledFlightListAggregationStrategy())
                    .setHeader(HEADER_LOWER_RANGE_ENDPOINT, simple("${bean:dateUtils.format(body.lowerEndpoint())}Z"))
                    .setHeader(HEADER_UPPER_RANGE_ENDPOINT, simple("${bean:dateUtils.format(body.upperEndpoint())}Z"))
                    .to("direct:fetchAirportFlightsByRangeAndStopVisitType").id("FetchFlightsByRangeAndStopVisitTypeProcessor")
                .end()
        ;

        from("direct:fetchAirportFlightsByRangeAndStopVisitType")
                .routeId("FetchFlightsByRangeAndStopVisitType")
                .process(exchange -> {exchange.getIn().setBody(StopVisitType.values());})
                .split(body(), new ScheduledFlightListAggregationStrategy())
                    .setHeader(HEADER_TIMETABLE_STOP_VISIT_TYPE, body())
                    .setHeader(HEADER_EXTIME_URI_PARAMETERS, simpleF("airport=${header.%s}&direction=${body.code}&PeriodFrom=${header.%s}&PeriodTo=${header.%s}",
                            HEADER_TIMETABLE_AIRPORT_IATA, HEADER_LOWER_RANGE_ENDPOINT, HEADER_UPPER_RANGE_ENDPOINT))
                    .log(LoggingLevel.DEBUG, this.getClass().getName(),
                            "Fetching flights for ${header.TimetableAirportIATA} by date range: ${header.LowerRangeEndpoint} - ${header.UpperRangeEndpoint}")
                    .to("direct:fetchFromHttpResource").id("FetchFromHttpResourceByRangeAndSVTProcessor")
                    .to("direct:splitJoinIncomingFlightMessages").id("SplitAndJoinRangeSVTFlightsProcessor")
                .end()
        ;

        from("direct:fetchFromHttpResource")
                .routeId("FetchFromHttpResource")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .setHeader(Exchange.HTTP_QUERY, simpleF("${header.%s}", HEADER_EXTIME_URI_PARAMETERS))
                .setHeader(HEADER_EXTIME_HTTP_URI).method(AvinorTimetableUtils.class, "useHttp4Client")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "HTTP URI HEADER: ${header.ExtimeHttpUri}")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "HTTP QUERY HEADER: ${header.CamelHttpQuery}")
                .setBody(constant(null))
                .toD("${header.ExtimeHttpUri}").id("FetchFromHttpResourceProcessor")
        ;

        from("direct:splitJoinIncomingFlightMessages")
                .routeId("FlightSplitterJoiner")
                .streamCaching()
                .split(stax(Flight.class, false), new ScheduledAirportFlightsAggregationStrategy()).streaming()
                    .wireTap("mock:wireTapEndpoint").id("FlightSplitWireTap")
                    /*.log(LoggingLevel.DEBUG, this.getClass().getName(),
                            "Processing flight with id: ${body.airlineDesignator}${body.flightNumber}")
                         .id("FlightSplitLogProcessor")*/
                .end()
        ;

        from("direct:convertToScheduledFlights")
                .routeId("ScheduledFlightsConverter")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Converting to scheduled flights")
                .bean(ScheduledFlightConverter.class, "convertToScheduledFlights")
                .to("direct:convertScheduledFlightsToNetex")
        ;

        from("direct:convertToDirectFlights")
                .routeId("DirectFlightsConverter")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Converting to scheduled direct flights")
                .bean(ScheduledFlightConverter.class, "convertToScheduledDirectFlights")
                //.to("direct:convertDirectFlightsToNetex")
        ;

        from("direct:convertToStopoverFlights")
                .routeId("StopoverFlightsConverter")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Converting to scheduled stopover flights")
                .bean(ScheduledFlightConverter.class, "convertToScheduledStopoverFlights")
                //.to("direct:convertStopoverFlightsToNetex")
        ;

        from("direct:convertScheduledFlightsToNetex")
                .routeId("ScheduledFlightsToNetexConverter")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Converting scheduled flights to NeTEx")
                .split(body()).parallelProcessing()
                    //.process(new DepartureIataInitProcessor())
                    //.enrich("direct:retrieveAirportNameResource", new DepartureIataEnricherAggregationStrategy())
                    //.process(new ArrivalIataInitProcessor())
                    //.enrich("direct:retrieveAirportNameResource", new ArrivalIataEnricherAggregationStrategy())
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
                    .to("file:target/netex")
                .end()
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

    class AirportIataProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            AirportIATA[] airportIATAs = Arrays.stream(AirportIATA.values())
                    .filter(iata -> !iata.equals(AirportIATA.OSL))
                    .toArray(AirportIATA[]::new);
            exchange.getIn().setBody(airportIATAs);
        }
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
        @SuppressWarnings("unchecked")
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            Object body = newExchange.getIn().getBody();
            if (oldExchange == null) {
                List<Flight> scheduledFlights = Lists.newArrayList();
                if (isCollection(body)) {
                    scheduledFlights.addAll((List<Flight>) body);
                }
                newExchange.getIn().setBody(scheduledFlights);
                return newExchange;
            } else {
                List<Flight> scheduledFlights = Collections.checkedList(
                        oldExchange.getIn().getBody(List.class), Flight.class);
                if (isCollection(body)) {
                    scheduledFlights.addAll((List<Flight>) body);
                }
                return oldExchange;
            }
        }

        private boolean isCollection(Object body) {
            return Collection.class.isInstance(body);
        }
    }

    class ScheduledAirportFlightsAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            StopVisitType stopVisitType = newExchange.getIn().getHeader(
                    HEADER_TIMETABLE_STOP_VISIT_TYPE, StopVisitType.class);
            Flight newFlight = newExchange.getIn().getBody(Flight.class);
            if (oldExchange == null) {
                List<Flight> flightList = new ArrayList<>();
                if (isValidFlight(stopVisitType, newFlight)) {
                    flightList.add(newFlight);
                }
                newExchange.getIn().setBody(flightList);
                return newExchange;
            } else {
                @SuppressWarnings("unchecked")
                List<Flight> flightList = Collections.checkedList(
                        oldExchange.getIn().getBody(List.class), Flight.class);
                if (isValidFlight(stopVisitType, newFlight)) {
                    flightList.add(newFlight);
                }
                return oldExchange;
            }
        }

        private boolean isValidFlight(StopVisitType stopVisitType, Flight newFlight) {
            switch (stopVisitType) {
                case ARRIVAL:
                    return AirportIATA.OSL.name().equalsIgnoreCase(newFlight.getDepartureStation());
                case DEPARTURE:
                    return isDomesticFlight(newFlight);
            }
            return false;
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
            ScheduledFlight originalBody = exchange.getIn().getBody(ScheduledFlight.class);
            exchange.setProperty(PROPERTY_SCHEDULED_FLIGHT_ORIGINAL_BODY, originalBody);
            String enrichParameter = originalBody.getDepartureAirportIATA();
            exchange.getIn().setBody(enrichParameter);
        }
    }

    class ArrivalIataInitProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            ScheduledFlight originalBody = exchange.getIn().getBody(ScheduledFlight.class);
            exchange.setProperty(PROPERTY_SCHEDULED_FLIGHT_ORIGINAL_BODY, originalBody);
            String enrichParameter = originalBody.getArrivalAirportIATA();
            exchange.getIn().setBody(enrichParameter);
        }
    }

    // Old processors, commented
/*
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
*/

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
            ScheduledFlight originalBody = original.getProperty(
                    PROPERTY_SCHEDULED_FLIGHT_ORIGINAL_BODY, ScheduledFlight.class);
            String resourceResponse = resource.getIn().getBody(String.class);
            //originalBody.setDepartureAirportName(resourceResponse);
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

    // Old aggregation strategies
/*
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
*/

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
