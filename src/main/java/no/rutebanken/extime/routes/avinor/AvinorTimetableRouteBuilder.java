package no.rutebanken.extime.routes.avinor;

import com.google.common.collect.Lists;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import no.rutebanken.extime.converter.CommonDataToNetexConverter;
import no.rutebanken.extime.converter.LineDataToNetexConverter;
import no.rutebanken.extime.converter.ScheduledFlightConverter;
import no.rutebanken.extime.model.*;
import no.rutebanken.extime.routes.BaseRouteBuilder;
import no.rutebanken.extime.services.MardukExchangeBlobStoreService;
import no.rutebanken.extime.util.AvinorTimetableUtils;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static no.rutebanken.extime.Constants.HEADER_MESSAGE_CORRELATION_ID;
import static no.rutebanken.extime.Constants.HEADER_MESSAGE_FILE_HANDLE;

@Component
public class AvinorTimetableRouteBuilder extends BaseRouteBuilder {

    private static final String HEADER_FILE_NAME_GENERATED = "FileNameGenerated";

    public static final String HEADER_MESSAGE_PROVIDER_ID = "RutebankenProviderId";
    public static final String HEADER_MESSAGE_FILE_NAME = "RutebankenFileName";
    public static final String HEADER_MESSAGE_USERNAME = "RutebankenUsername";

    private static final String PROPERTY_LINE_DATASET_ORIGINAL_BODY = "LineDataSetOriginalBody";
    private static final String PROPERTY_LINE_DATASETS_LIST_ORIGINAL_BODY = "LineDataSetsListOriginalBody";

    private static final String DEFAULT_NETEX_CHARSET_NAME = StandardCharsets.UTF_8.name();
    private static final String PROP_AIRPORT_NAME = "RutebankenAirportName";

    private final MardukExchangeBlobStoreService mardukExchangeBlobStoreService;
    private final FlightEventMapper flightEventMapper;


    public AvinorTimetableRouteBuilder(MardukExchangeBlobStoreService mardukExchangeBlobStoreService) {
        this.mardukExchangeBlobStoreService = mardukExchangeBlobStoreService;
        flightEventMapper = new FlightEventMapper();
    }

    @Override
    public void configure() {

        super.configure();

        JaxbDataFormat netexJaxbDataFormat = new JaxbDataFormat();
        netexJaxbDataFormat.setContextPath(PublicationDeliveryStructure.class.getPackage().getName());
        netexJaxbDataFormat.setPrettyPrint(true);
        netexJaxbDataFormat.setEncoding(DEFAULT_NETEX_CHARSET_NAME);

        JaxbDataFormat airportJaxbDataFormat = new JaxbDataFormat();
        airportJaxbDataFormat.setContextPath(Flights.class.getPackage().getName());
        airportJaxbDataFormat.setEncoding(DEFAULT_NETEX_CHARSET_NAME);

        from("{{avinor.timetable.scheduler.consumer}}")
            .to("direct:refreshStops")
            .choice()
            .when(simple("${properties:avinor.timetable.dump.input:false}", Boolean.class))
            .to("direct:fetchFlightsFromDump")
            .otherwise()
            .to("direct:fetchFlights")
            .end()
            .to("direct:convertFlights")
            .routeId("AvinorTimetableSchedulerStarter");

        // for test/debugging
        from("direct:fetchFlightsFromDump")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Fetching data from dump")
                .bean(AvinorTimetableUtils.class, "generateFlightEventsFromFeedDump(${properties:avinor.timetable.dump.input.path})")
                .routeId("FetchFlightsFromDump");

        from("direct:fetchFlights")
            .log(LoggingLevel.INFO, this.getClass().getName(), "Fetching data from feed")
            .bean(FlightRequestBuilder.class, "generateFlightRequests")
            .split(body(), new FlightEventListAggregationStrategy())
                .parallelProcessing()
                .log(LoggingLevel.INFO, this.getClass().getName(),
                        "Fetching flights for ${body.airportName} and date range : [${body.fromDate} , ${body.toDate}]")
                .to("direct:fetchTimetableForAirport")
            .end()
            .routeId("FetchFlights");

        from("direct:fetchTimetableForAirport")
            .throttle(1)
            .timePeriodMillis(100)
            .log(LoggingLevel.DEBUG, this.getClass().getName(), "Sending request ${body.request}")
            .setProperty(PROP_AIRPORT_NAME, simple("${body.airportName}"))
            .setBody(simple("${body.request}"))
            .process(exchange -> exchange.getIn().removeHeaders("*"))
            .toD("${body}")
            .id("FetchXmlFromHttpFeedProcessor")
            .end()
            .choice()
            .when(simple("${properties:avinor.timetable.dump.output:false}", Boolean.class))
            .setHeader(Exchange.FILE_NAME, simple("${exchangeProperty:" + PROP_AIRPORT_NAME + "}.xml"))
            .to("file:{{avinor.timetable.dump.output.path}}")
            .end()
            // convert using the charset retrieved from the HTTP response header that Camel sets in the property Exchange.CHARSET_NAME
            .unmarshal(airportJaxbDataFormat)
            .setBody(exchange -> flightEventMapper.mapToFlightEvent(exchange.getIn().getBody(Flights.class)))
            .log(LoggingLevel.DEBUG, this.getClass().getName(), "Retrieved ${body.size} flight events")
            // remove the property Exchange.CHARSET_NAME after the conversion so that the JAXB formats can be overriden with a custom encoding
            .removeProperty(Exchange.CHARSET_NAME)
            .routeId("FetchTimetableForAirport");

        from("direct:convertFlights")
            .routeId("ConvertFlights")
            .log(LoggingLevel.INFO, this.getClass().getName(), "Converting to line centric flight data sets")
            .bean(ScheduledFlightConverter.class, "convertFlightEventsToLineCentricDataSets")
            .id("ConvertToLineDataSetsBeanProcessor")
            .setProperty(PROPERTY_LINE_DATASETS_LIST_ORIGINAL_BODY, body())
            .to("direct:convertCommonDataToNetex")
            .setBody(simpleF("${exchangeProperty[%s]}", List.class, PROPERTY_LINE_DATASETS_LIST_ORIGINAL_BODY))
            .to("direct:convertLineDataSetsToNetex")
            .to("direct:compressNetexAndSendToStorage");


        from("direct:convertCommonDataToNetex")
            .routeId("CommonDataToNetexConverter")
            .log(LoggingLevel.INFO, "Converting common line data to NeTEx")
            .log(LoggingLevel.INFO, this.getClass().getName(), "Cleaning NeTEx output directory : ${properties:netex.generated.output.path}")
            .process(exchange ->
             {
                Path outputPath = Paths.get(exchange.getContext().resolvePropertyPlaceholders("{{netex.generated.output.path}}"));
                Files.createDirectories(outputPath);
                 try (Stream<Path> list = Files.list(outputPath)) {
                     list
                             .filter(Files::isRegularFile)
                             .map(Path::toFile)
                             .forEach(File::delete);
                 }
             })
            .id("CleanNetexOutputPathProcessor")
            .log(LoggingLevel.INFO, this.getClass().getName(), "Converting common aviation data to NeTEx")
            .bean(CommonDataToNetexConverter.class, "convertToNetex")
            .id("ConvertCommonDataToNetexProcessor")
            .marshal(netexJaxbDataFormat)
            .process(exchange -> exchange.getIn().setHeader(HEADER_FILE_NAME_GENERATED, "_avinor_common_elements"))
            .id("GenerateCommonFileNameProcessor")
            .setHeader(Exchange.FILE_NAME, simpleF("${header.%s}.xml", HEADER_FILE_NAME_GENERATED))
            .to("file:{{netex.generated.output.path}}");

        from("direct:convertLineDataSetsToNetex")
            .routeId("LineDataSetsToNetexConverter")
            .log(LoggingLevel.INFO, this.getClass().getName(), "Converting line centric data sets to NeTEx")
            .split(body())
                .process(exchange -> {
                    LineDataSet originalBody = exchange.getIn().getBody(LineDataSet.class);
                    exchange.setProperty(PROPERTY_LINE_DATASET_ORIGINAL_BODY, originalBody);
                    String enrichParameter = originalBody.getAirlineIata();
                    exchange.getIn().setBody(enrichParameter);
                })
                .id("AirlineIataPreEnrichProcessor")
                .enrich("direct:enrichWithAirlineName", new AirlineNameEnricherAggregationStrategy())
                .bean(LineDataToNetexConverter.class, "convertToNetex")
                .id("ConvertLineDataSetsToNetexProcessor")
                .setHeader(HEADER_FILE_NAME_GENERATED, simple("${bean:avinorTimetableUtils?method=generateFilename}"))
                .marshal(netexJaxbDataFormat)
                .setHeader(Exchange.FILE_NAME, simpleF("${header.%s}.xml", HEADER_FILE_NAME_GENERATED))
                .to("file:{{netex.generated.output.path}}")
            .end();

        from("direct:enrichWithAirlineName")
                .filter().method(ScheduledFlightConverter.class, "isKnownAirlineName")
                .bean(ScheduledFlightConverter.class, "getKnownAirlineName");

        from("direct:compressNetexAndSendToStorage")
            .routeId("CompressNetexAndSendToStorage")
            .log(LoggingLevel.INFO, "Compressing XML files and send to storage")
            .setHeader(Exchange.FILE_NAME, simple("avinor-netex_${bean:dateUtils.timestamp()}.zip"))
            .bean(AvinorTimetableUtils.class, "compressNetexFiles")
            .log(LoggingLevel.INFO, this.getClass().getName(), "Done compressing all files to zip archive : ${header.CamelFileName}")
            .process(exchange -> exchange.getIn().setHeader(HEADER_MESSAGE_CORRELATION_ID, UUID.randomUUID().toString()))
            .setHeader(HEADER_MESSAGE_FILE_HANDLE, simple("${properties:blobstore.blob.path}${header.CamelFileName}"))
            .bean(mardukExchangeBlobStoreService, "uploadBlob")
            .id("UploadZipToBlobStore")
            .log(LoggingLevel.INFO, this.getClass().getName(), "Done storage upload of file : ${header.CamelFileName}")
            .to("direct:notifyMarduk");

        from("direct:notifyMarduk")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Notifying marduk queue about NeTEx export")
                .setHeader(HEADER_MESSAGE_PROVIDER_ID, simple("${properties:blobstore.provider.id}", Long.class))
                .setHeader(HEADER_MESSAGE_FILE_NAME, simple("${header.CamelFileName}"))
                .setHeader(HEADER_MESSAGE_USERNAME, simple("Extime"))
                .setBody(constant(""))
                .to("google-pubsub:{{extime.pubsub.project.id}}:{{queue.upload.destination.name}}")
                .routeId("NotifyMarduk");

    }


    private static class FlightEventListAggregationStrategy implements AggregationStrategy {
        @Override
        @SuppressWarnings("unchecked")
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            Object body = newExchange.getIn().getBody();

            if (oldExchange == null) {
                List<FlightEvent> flightEvents = Lists.newArrayList();

                if (isCollection(body)) {
                    flightEvents.addAll((List<FlightEvent>) body);
                }

                newExchange.getIn().setBody(flightEvents);
                return newExchange;
            } else {
                List<FlightEvent> flightEvents = Collections.checkedList(
                        oldExchange.getIn().getBody(List.class), FlightEvent.class);

                if (isCollection(body)) {
                    flightEvents.addAll((List<FlightEvent>) body);
                }

                return oldExchange;
            }
        }

        private boolean isCollection(Object body) {
            return body instanceof Collection;
        }
    }


    private static class AirlineNameEnricherAggregationStrategy implements AggregationStrategy {
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
