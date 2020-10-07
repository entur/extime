package no.rutebanken.extime.routes.avinor;

import com.google.common.collect.Lists;
import no.avinor.flydata.xjc.model.airline.AirlineName;
import no.avinor.flydata.xjc.model.airline.AirlineNames;
import no.avinor.flydata.xjc.model.airport.AirportName;
import no.avinor.flydata.xjc.model.airport.AirportNames;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import no.rutebanken.extime.converter.CommonDataToNetexConverter;
import no.rutebanken.extime.converter.LineDataToNetexConverter;
import no.rutebanken.extime.converter.ScheduledFlightConverter;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.LineDataSet;
import no.rutebanken.extime.model.StopVisitType;
import no.rutebanken.extime.util.AvinorTimetableUtils;
import no.rutebanken.extime.util.DateUtils;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.zipfile.ZipAggregationStrategy;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_HTTP_URI;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_RESOURCE_CODE;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_URI_PARAMETERS;
import static org.apache.camel.component.stax.StAXBuilder.stax;

@Component
public class AvinorTimetableRouteBuilder extends RouteBuilder {

    public static final String HEADER_TIMETABLE_SMALL_AIRPORT_RANGE = "TimetableSmallAirportRange";
    public static final String HEADER_TIMETABLE_LARGE_AIRPORT_RANGE = "TimetableLargeAirportRange";
    public static final String HEADER_MESSAGE_CORRELATION_ID = "RutebankenCorrelationId";
    public static final String PROPERTY_STATIC_FLIGHTS_XML_FILE = "StaticFlightXmlFile";
    public static final String PROPERTY_OFFLINE_MODE = "OfflineMode";

    static final String HEADER_TIMETABLE_STOP_VISIT_TYPE = "TimetableStopVisitType";
    static final String HEADER_LOWER_RANGE_ENDPOINT = "LowerRangeEndpoint";
    static final String HEADER_UPPER_RANGE_ENDPOINT = "UpperRangeEndpoint";

    private static final String HEADER_FILE_NAME_GENERATED = "FileNameGenerated";

    public static final String HEADER_MESSAGE_PROVIDER_ID = "RutebankenProviderId";
    public static final String HEADER_MESSAGE_FILE_HANDLE = "RutebankenFileHandle";
    public static final String HEADER_MESSAGE_FILE_NAME = "RutebankenFileName";

    private static final String PROPERTY_LINE_DATASET_ORIGINAL_BODY = "LineDataSetOriginalBody";
    private static final String PROPERTY_LINE_DATASETS_LIST_ORIGINAL_BODY = "LineDataSetsListOriginalBody";

    @Override
    public void configure() throws Exception {

        JaxbDataFormat jaxbDataFormat = new JaxbDataFormat();
        jaxbDataFormat.setContextPath(PublicationDeliveryStructure.class.getPackage().getName());

        jaxbDataFormat.setPrettyPrint(true);
        jaxbDataFormat.setEncoding("UTF-8");

        JaxbDataFormat flightsJaxbDataFormat = new JaxbDataFormat();
        flightsJaxbDataFormat.setContextPath(Flights.class.getPackage().getName());

        from("{{avinor.timetable.scheduler.consumer}}")
                .routeId("AvinorTimetableSchedulerStarter")
                .process(exchange -> {
                    String staticDataFile = System.getProperty("avinor.timetable.dump.file");
                    exchange.setProperty(PROPERTY_STATIC_FLIGHTS_XML_FILE, StringUtils.isNotEmpty(staticDataFile) ? staticDataFile : null);
                })
                .choice()
                    .when(simple("${properties:avinor.timetable.dump.enabled:false} == true"))
                        .to("direct:fetchFlights")
                        .to("direct:dumpFetchedFlightsToFile")
                    .when(simpleF("${exchangeProperty[%s]} != null", String.class, PROPERTY_STATIC_FLIGHTS_XML_FILE))
                        .log(LoggingLevel.INFO, this.getClass().getName(), "Static run from XML file : ${exchangeProperty[StaticFlightXmlFile]}")
                        .setProperty(PROPERTY_OFFLINE_MODE, simple("true", Boolean.class))
                        .bean(AvinorTimetableUtils.class, "generateFlightsFromFeedDump")
                        .to("direct:convertFlights")
                    .otherwise()
                        .setProperty(PROPERTY_OFFLINE_MODE, simple("false", Boolean.class))
                        .to("direct:fetchFlights")
                        .to("direct:convertFlights")
                .end()
        ;

        from("direct:fetchFlights")
                .routeId("FetchFlights")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Fetching data from feed")
                .process(new AirportIataProcessor()).id("TimetableAirportIATAProcessor")
                .bean(DateUtils.class, "generateDateRanges").id("TimetableDateRangeProcessor")
                .split(body(), new ScheduledFlightListAggregationStrategy()).parallelProcessing()
                    .log(LoggingLevel.INFO, this.getClass().getName(), "==========================================")
                    .log(LoggingLevel.INFO, this.getClass().getName(), "Processing airport with IATA code: ${body}")
                    .setHeader(HEADER_EXTIME_RESOURCE_CODE, simple("${body}"))
                    .to("direct:fetchTimetableForAirport").id("FetchTimetableProcessor")
                    .log(LoggingLevel.INFO, this.getClass().getName(), "Flights fetched for ${header.ExtimeResourceCode}")
                .end()
        ;

        from("direct:convertFlights")
                .routeId("ConvertFlights")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Converting to line centric flight data sets")
                .bean(ScheduledFlightConverter.class, "convertToLineCentricDataSets").id("ConvertToLineDataSetsBeanProcessor")
                .setProperty(PROPERTY_LINE_DATASETS_LIST_ORIGINAL_BODY, body())
                .to("direct:convertCommonDataToNetex")
                .setBody(simpleF("${exchangeProperty[%s]}", List.class, PROPERTY_LINE_DATASETS_LIST_ORIGINAL_BODY))
                .to("direct:convertLineDataSetsToNetex")
                .to("direct:compressNetexAndSendToStorage")
        ;

        from("direct:fetchAndCacheAirportName")
                .routeId("FetchAndCacheAirportName")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetching airport name by IATA code: ${header.ExtimeResourceCode}")
                .setHeader(HEADER_EXTIME_HTTP_URI, simple("{{avinor.airport.feed.endpoint}}"))
                .setHeader(HEADER_EXTIME_URI_PARAMETERS, simpleF("airport=${header.%s}&shortname=Y&ukname=Y", HEADER_EXTIME_RESOURCE_CODE))
                .convertBodyTo(String.class)
                .to("direct:fetchXmlStreamFromHttpFeed").id("FetchAirportNameFromHttpFeedProcessor")
                .convertBodyTo(String.class)
                .unmarshal(new JaxbDataFormat("no.avinor.flydata.xjc.model.airport"))

                .process(exchange -> {
                    List<AirportName> airportNames = exchange.getIn().getBody(AirportNames.class).getAirportName();
                    exchange.getIn().setBody((airportNames != null && !airportNames.isEmpty()) ? airportNames.get(0).getName() :
                            exchange.getIn().getHeader(HEADER_EXTIME_RESOURCE_CODE), String.class);
                })

                .to("direct:addResourceToCache")
        ;

        from("direct:fetchAndCacheAirlineName")
                .routeId("FetchAndCacheAirlineName")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetching airline name by IATA code: ${header.ExtimeResourceCode}")
                .setHeader(HEADER_EXTIME_HTTP_URI, simple("{{avinor.airline.feed.endpoint}}"))
                .setHeader(HEADER_EXTIME_URI_PARAMETERS, simpleF("airline=${header.%s}", HEADER_EXTIME_RESOURCE_CODE))
                .to("direct:fetchXmlStreamFromHttpFeed").id("FetchAirlineNameFromHttpFeedProcessor")
                .unmarshal(new JaxbDataFormat("no.avinor.flydata.xjc.model.airline"))

                .process(exchange -> {
                    List<AirlineName> airlineNames = exchange.getIn().getBody(AirlineNames.class).getAirlineName();
                    exchange.getIn().setBody((airlineNames != null && !airlineNames.isEmpty()) ? airlineNames.get(0).getName() :
                            exchange.getIn().getHeader(HEADER_EXTIME_RESOURCE_CODE), String.class);
                })

                .to("direct:addResourceToCache")
        ;

        from("direct:fetchTimetableForAirport")
                .routeId("FetchTimetableForAirport")

                .choice()
                    .when(simpleF("${header.%s} in ${properties:avinor.airports.large}", HEADER_EXTIME_RESOURCE_CODE))
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

        from("direct:fetchTimetableForAirportByRanges")
                .routeId("FetchTimetableForAirportByDateRanges")
                .setHeader(HEADER_EXTIME_HTTP_URI, simple("{{avinor.timetable.feed.endpoint}}"))

                .split(body(), new ScheduledFlightListAggregationStrategy())
                    .setHeader(HEADER_LOWER_RANGE_ENDPOINT, simple("${bean:dateUtils.format(${body.lowerEndpoint()})}Z"))
                    .setHeader(HEADER_UPPER_RANGE_ENDPOINT, simple("${bean:dateUtils.format(${body.upperEndpoint()})}Z"))
                    .to("direct:fetchAirportFlightsByRangeAndStopVisitType").id("FetchFlightsByRangeAndStopVisitTypeProcessor")
                .end()
        ;

        from("direct:fetchAirportFlightsByRangeAndStopVisitType")
                .routeId("FetchFlightsByRangeAndStopVisitType")
                .process(exchange -> exchange.getIn().setBody(StopVisitType.values()))

                .split(body(), new ScheduledFlightListAggregationStrategy())
                    .setHeader(HEADER_TIMETABLE_STOP_VISIT_TYPE, body())
                    .setHeader(HEADER_EXTIME_URI_PARAMETERS, simpleF("airport=${header.%s}&direction=${body.code}&PeriodFrom=${header.%s}&PeriodTo=${header.%s}",
                            HEADER_EXTIME_RESOURCE_CODE, HEADER_LOWER_RANGE_ENDPOINT, HEADER_UPPER_RANGE_ENDPOINT))
                    .log(LoggingLevel.DEBUG, this.getClass().getName(),
                            "Fetching flights for ${header.ExtimeResourceCode} by date range: ${header.LowerRangeEndpoint} - ${header.UpperRangeEndpoint}")
                    .to("direct:fetchXmlStreamFromHttpFeed").id("FetchFromHttpFeedByRangeAndSVTProcessor")
                .process(e->
                toString())
                    .to("direct:splitJoinIncomingFlightMessages").id("SplitAndJoinRangeSVTFlightsProcessor")
                .end()
        ;

        from("direct:splitJoinIncomingFlightMessages")
                .routeId("FlightSplitterJoiner")
                .streamCaching()

                .split(stax(Flight.class, false), new ScheduledAirportFlightsAggregationStrategy()).streaming()
                .wireTap("direct:wireTapFlightSplitter").id("FlightSplitWireTap")
                .end()
        ;

         from("direct:wireTapFlightSplitter")
                 .process(e -> {/* Do nothing. WireTap used by test. This should not have been implemented as split - aggregate */} )
                 .routeId("DoNothingFlightSplitWireTap");

        final String contentType = "text/xml;charset=utf-8";
        final String charsetName = "utf-8";

        from("direct:convertCommonDataToNetex")
                .routeId("CommonDataToNetexConverter")
                .log(LoggingLevel.INFO, "Converting common line data to NeTEx")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Cleaning NeTEx output directory : ${properties:netex.generated.output.path}")
                .process(exchange -> Files.list(Paths.get(exchange.getContext().resolvePropertyPlaceholders("{{netex.generated.output.path}}")))
                        .filter(Files::isRegularFile)
                        .map(Path::toFile)
                        .forEach(File::delete)
                ).id("CleanNetexOutputPathProcessor")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Converting common aviation data to NeTEx")
                .bean(CommonDataToNetexConverter.class, "convertToNetex").id("ConvertCommonDataToNetexProcessor")
                .marshal(jaxbDataFormat)
                //.log(LoggingLevel.DEBUG, this.getClass().getName(), "${body}")
                .process(exchange -> exchange.getIn().setHeader(HEADER_FILE_NAME_GENERATED, "_avinor_common_elements")).id("GenerateCommonFileNameProcessor")
                .setHeader(Exchange.FILE_NAME, simpleF("${header.%s}.xml", HEADER_FILE_NAME_GENERATED))
                .setHeader(Exchange.CONTENT_TYPE, constant(contentType))
                .setHeader(Exchange.CHARSET_NAME, constant(charsetName))
                .to("file:{{netex.generated.output.path}}")
        ;

        from("direct:convertLineDataSetsToNetex")
                .routeId("LineDataSetsToNetexConverter")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Converting line centric data sets to NeTEx")
                .split(body())//.parallelProcessing()
                    .process(exchange -> {
                        LineDataSet originalBody = exchange.getIn().getBody(LineDataSet.class);
                        exchange.setProperty(PROPERTY_LINE_DATASET_ORIGINAL_BODY, originalBody);
                        String enrichParameter = originalBody.getAirlineIata();
                        exchange.getIn().setBody(enrichParameter);
                    }).id("AirlineIataPreEnrichProcessor")
                    .enrich("direct:enrichWithAirlineName", new AirlineNameEnricherAggregationStrategy())
                    .bean(LineDataToNetexConverter.class, "convertToNetex").id("ConvertLineDataSetsToNetexProcessor")
                    .setHeader(HEADER_FILE_NAME_GENERATED, simple("${bean:avinorTimetableUtils?method=generateFilename}"))
                    .marshal(jaxbDataFormat)
                    .setHeader(Exchange.FILE_NAME, simpleF("${header.%s}.xml", HEADER_FILE_NAME_GENERATED))
                    .setHeader(Exchange.CONTENT_TYPE, constant(contentType))
                    .setHeader(Exchange.CHARSET_NAME, constant(charsetName))
                    .to("file:{{netex.generated.output.path}}")
                .end()
        ;

        from("direct:enrichWithAirlineName")
                .routeId("AirlineNameEnricher")
                .choice()
                    .when().method(ScheduledFlightConverter.class, "isKnownAirlineName")
                        .bean(ScheduledFlightConverter.class, "getKnownAirlineName")
                    .otherwise()
                        .choice()
                            .when(simpleF("${exchangeProperty[%s]} == true", Boolean.class, PROPERTY_OFFLINE_MODE))
                                .log("Unknown airline IATA found, ${body} [OFFLINE MODE]")
                                .process(exchange -> exchange.getIn().setBody(exchange.getIn().getBody(String.class).toUpperCase(), String.class))
                            .otherwise()
                                .log("Unknown airline IATA found, ${body}, fetching name from feed")
                                .setHeader(HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT, constant("direct:fetchAndCacheAirlineName"))
                                .to("direct:retrieveResource")
                        .end()
                .end()
        ;

        from("file:{{netex.generated.output.path}}?noop=true&idempotent=true&antInclude=**/*.xml")
                .routeId("CompressAndSendToStorage")
                .autoStartup(false)

                .aggregate(new ZipAggregationStrategy(false, true))
                    .constant(true)
                    .completionFromBatchConsumer()
                    .eagerCheckCompletion()

                .setHeader(Exchange.FILE_NAME, simple("avinor-netex_${bean:dateUtils.timestamp()}.zip"))
                .to("file:{{netex.compressed.output.path}}")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Done compressing all files to zip archive : ${header.CamelFileName}")

                .process(exchange -> exchange.getIn().setHeader(HEADER_MESSAGE_CORRELATION_ID, UUID.randomUUID().toString()))
                // .bean(AvinorTimetableUtils.class, "uploadBlobToStorage").id("UploadZipToBlobStore")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Done storage upload of file : ${header.CamelFileName}")

                .setHeader(HEADER_MESSAGE_PROVIDER_ID, simple("${properties:blobstore.provider.id}", Long.class))
                .setHeader(HEADER_MESSAGE_FILE_HANDLE, simple("${properties:blobstore.blob.path}${header.CamelFileName}"))
                .setHeader(HEADER_MESSAGE_FILE_NAME, simple("${header.CamelFileName}"))
                .setBody(constant(null))

                .log(LoggingLevel.INFO, this.getClass().getName(), "Notifying marduk queue about NeTEx export")
                .to("entur-google-pubsub:{{queue.upload.destination.name}}")

                .process(exchange -> {
                    Thread stop = new Thread(() -> {
                        try {
                            exchange.getContext().stopRoute(exchange.getFromRouteId());
                        } catch (Exception ignored) {
                            // Ignore
                        }
                    });
                    stop.start();
                })
        ;

        from("direct:compressNetexAndSendToStorage")
                .routeId("CompressNetexAndSendToStorage")
                .log(LoggingLevel.INFO, "Compressing XML files and send to storage")
                .setHeader(Exchange.FILE_NAME, simple("avinor-netex_${bean:dateUtils.timestamp()}.zip"))
                .bean(AvinorTimetableUtils.class, "compressNetexFiles")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Done compressing all files to zip archive : ${header.CamelFileName}")

                .process(exchange -> exchange.getIn().setHeader(HEADER_MESSAGE_CORRELATION_ID, UUID.randomUUID().toString()))
                .bean(AvinorTimetableUtils.class, "uploadBlobToStorage").id("UploadZipToBlobStore")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Done storage upload of file : ${header.CamelFileName}")

                .setHeader(HEADER_MESSAGE_PROVIDER_ID, simple("${properties:blobstore.provider.id}", Long.class))
                .setHeader(HEADER_MESSAGE_FILE_HANDLE, simple("${properties:blobstore.blob.path}${header.CamelFileName}"))
                .setHeader(HEADER_MESSAGE_FILE_NAME, simple("${header.CamelFileName}"))
                .setBody(constant(null))

                .log(LoggingLevel.INFO, this.getClass().getName(), "Notifying marduk queue about NeTEx export")
                .to("entur-google-pubsub:{{queue.upload.destination.name}}")
        ;

        from("direct:dumpFetchedFlightsToFile")
                .autoStartup("{{avinor.timetable.dump.enabled}}")
                .routeId("FetchedFlightsDumper")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Dumping fetched flights to file")
                .bean(AvinorTimetableUtils.class, "createFlightsElement")
                .marshal(flightsJaxbDataFormat)
                .setHeader(Exchange.FILE_NAME, simple("avinor-flights_${bean:dateUtils.timestamp()}.xml"))
                .setHeader(Exchange.CONTENT_TYPE, constant(contentType))
                .setHeader(Exchange.CHARSET_NAME, constant(charsetName))
                .to("file:{{avinor.timetable.dump.output.path}}")
                .log(LoggingLevel.INFO, "Successfully dumped all flights to file : ${header.CamelFileNameProduced}")
        ;
    }

    private class AirportIataProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            AirportIATA[] airportIATAs = Arrays.stream(AirportIATA.values())
                    .filter(iata -> !iata.equals(AirportIATA.OSL))
                    .toArray(AirportIATA[]::new);
            exchange.getIn().setBody(airportIATAs);
        }
    }

    private class ScheduledFlightListAggregationStrategy implements AggregationStrategy {
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

    private class ScheduledAirportFlightsAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            StopVisitType stopVisitType = newExchange.getIn().getHeader(
                    HEADER_TIMETABLE_STOP_VISIT_TYPE, StopVisitType.class);
            Flight newFlight = newExchange.getIn().getBody(Flight.class);

            if (oldExchange == null) {
                List<Flight> flightList = new ArrayList<>();

                if (AvinorTimetableUtils.isValidFlight(stopVisitType, newFlight)) {
                    flightList.add(newFlight);
                }

                newExchange.getIn().setBody(flightList);
                return newExchange;
            } else {
                @SuppressWarnings("unchecked")
                List<Flight> flightList = Collections.checkedList(
                        oldExchange.getIn().getBody(List.class), Flight.class);

                if (AvinorTimetableUtils.isValidFlight(stopVisitType, newFlight)) {
                    flightList.add(newFlight);
                }

                return oldExchange;
            }
        }
    }

    private class AirlineNameEnricherAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange original, Exchange resource) {
            LineDataSet originalBody = original.getProperty(PROPERTY_LINE_DATASET_ORIGINAL_BODY, LineDataSet.class);
            String resourceResponse = resource.getIn().getBody(String.class);
            originalBody.setAirlineName(resourceResponse);
            original.getIn().setBody(originalBody);
            return original;
        }
    }

}
