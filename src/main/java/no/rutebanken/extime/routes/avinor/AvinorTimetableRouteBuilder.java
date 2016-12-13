package no.rutebanken.extime.routes.avinor;

import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_HTTP_URI;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_RESOURCE_CODE;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_URI_PARAMETERS;
import static org.apache.camel.component.stax.StAXBuilder.stax;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBElement;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.zipfile.ZipAggregationStrategy;
import org.rutebanken.netex.model.CompositeFrame;
import org.rutebanken.netex.model.Line;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ServiceFrame;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import no.avinor.flydata.xjc.model.airline.AirlineName;
import no.avinor.flydata.xjc.model.airline.AirlineNames;
import no.avinor.flydata.xjc.model.airport.AirportName;
import no.avinor.flydata.xjc.model.airport.AirportNames;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.converter.CommonDataToNetexConverter;
import no.rutebanken.extime.converter.ScheduledFlightConverter;
import no.rutebanken.extime.converter.ScheduledFlightToNetexConverter;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.ScheduledDirectFlight;
import no.rutebanken.extime.model.ScheduledFlight;
import no.rutebanken.extime.model.ScheduledStopover;
import no.rutebanken.extime.model.ScheduledStopoverFlight;
import no.rutebanken.extime.model.StopVisitType;
import no.rutebanken.extime.util.AvinorTimetableUtils;
import no.rutebanken.extime.util.DateUtils;

@Component
public class AvinorTimetableRouteBuilder extends RouteBuilder { //extends BaseRouteBuilder {

    public static final String HEADER_TIMETABLE_SMALL_AIRPORT_RANGE = "TimetableSmallAirportRange";
    public static final String HEADER_TIMETABLE_LARGE_AIRPORT_RANGE = "TimetableLargeAirportRange";
    public static final String HEADER_MESSAGE_CORRELATION_ID = "RutebankenCorrelationId";

    static final String HEADER_TIMETABLE_STOP_VISIT_TYPE = "TimetableStopVisitType";
    static final String HEADER_LOWER_RANGE_ENDPOINT = "LowerRangeEndpoint";
    static final String HEADER_UPPER_RANGE_ENDPOINT = "UpperRangeEndpoint";
    static final String HEADER_STOPOVER_FLIGHT_ORIGINAL_BODY = "StopoverFlightOriginalBody";
    static final String HEADER_FILE_NAME_GENERATED = "FileNameGenerated";

    private static final String HEADER_MESSAGE_PROVIDER_ID = "RutebankenProviderId";
    private static final String HEADER_MESSAGE_FILE_HANDLE = "RutebankenFileHandle";
    private static final String HEADER_MESSAGE_FILE_NAME = "RutebankenFileName";

    private static final String PROPERTY_DIRECT_FLIGHT_ORIGINAL_BODY = "DirectFlightOriginalBody";
    private static final String PROPERTY_SCHEDULED_FLIGHT_ORIGINAL_BODY = "ScheduledFlightOriginalBody";
    private static final String PROPERTY_SCHEDULED_FLIGHT_LIST_ORIGINAL_BODY = "ScheduledFlightListOriginalBody";

    static final String PROPERTY_STOPOVER_ORIGINAL_BODY = "StopoverOriginalBody";

    @Override
    public void configure() throws Exception {
        // @todo: look over need for a superclass, and general error handling.
        // @todo: consider moving all preprocessors inside routes instead, to make it clearer
        //super.configure();
        //getContext().setTracing(true);

        JaxbDataFormat jaxbDataFormat = new JaxbDataFormat();
        jaxbDataFormat.setContextPath(PublicationDeliveryStructure.class.getPackage().getName());
        jaxbDataFormat.setSchema("classpath:/xsd/NeTEx-XML-1.04beta/schema/xsd/NeTEx_publication.xsd"); // @TODO: use schema from netex-java-model instead
        //jaxbDataFormat.setNamespacePrefixRef(NAMESPACE_PREFIX_REF);
        jaxbDataFormat.setPrettyPrint(true);
        jaxbDataFormat.setEncoding("UTF-8");

        
        
        // @todo: enable parallell processing when going into test/beta/prod
        from("{{avinor.timetable.scheduler.consumer}}")
                .routeId("AvinorTimetableSchedulerStarter")

                .process(new AirportIataProcessor()).id("TimetableAirportIATAProcessor")
                .bean(DateUtils.class, "generateDateRanges").id("TimetableDateRangeProcessor")

                .split(body(), new ScheduledFlightListAggregationStrategy())//.parallelProcessing()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "==========================================")
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Processing airport with IATA code: ${body}")
                    .setHeader(HEADER_EXTIME_RESOURCE_CODE, simple("${body}"))
                    .to("direct:fetchAndCacheAirportName").id("FetchAirportNameProcessor")
                    .to("direct:fetchTimetableForAirport").id("FetchTimetableProcessor")
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Flights fetched for ${header.ExtimeResourceCode}")
                .end()

                // 1st alternative run, with static test data from file
                //.bean(AvinorTimetableUtils.class, "generateStaticFlights") // TODO: remove when going beta

                // 2nd alternative run, with live test data from feed dump
                //.bean(AvinorTimetableUtils.class, "generateFlightsFromFeedDump") // TODO: remove when going beta

                .log(LoggingLevel.INFO, this.getClass().getName(), "Converting to scheduled flights")
                .bean(ScheduledFlightConverter.class, "convertToScheduledFlights").id("ConvertToScheduledFlightsBeanProcessor")
                .setProperty(PROPERTY_SCHEDULED_FLIGHT_LIST_ORIGINAL_BODY, body())

                .log(LoggingLevel.INFO, "Converting common aviation data to NeTEx")
                .to("direct:convertCommonDataToNetex")

                .setBody(simpleF("exchangeProperty[%s]", List.class, PROPERTY_SCHEDULED_FLIGHT_LIST_ORIGINAL_BODY))
                .log(LoggingLevel.INFO, "Converting flights to NeTEx")
                .to("direct:convertScheduledFlightsToNetex")
                .log(LoggingLevel.INFO, "Compressing XML files and send to storage")
                .to("controlbus:route?routeId=CompressAndSendToStorage&action=start")
        ;

        from("direct:fetchAndCacheAirportName")
                .routeId("FetchAndCacheAirportName")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetching airport name by IATA code: ${header.ExtimeResourceCode}")
                .setHeader(HEADER_EXTIME_HTTP_URI, simple("{{avinor.airport.feed.endpoint}}"))
                .setHeader(HEADER_EXTIME_URI_PARAMETERS, simpleF("airport=${header.%s}&shortname=Y&ukname=Y", HEADER_EXTIME_RESOURCE_CODE))
                .to("direct:fetchXmlStreamFromHttpFeed").id("FetchAirportNameFromHttpFeedProcessor")
                .convertBodyTo(AirportNames.class)

                .process(exchange -> {
                    List<AirportName> airportNames = exchange.getIn().getBody(AirportNames.class).getAirportName();
                    exchange.getIn().setBody((airportNames != null && airportNames.size() > 0) ? airportNames.get(0).getName() :
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
                .convertBodyTo(AirlineNames.class)

                .process(exchange -> {
                    List<AirlineName> airlineNames = exchange.getIn().getBody(AirlineNames.class).getAirlineName();
                    exchange.getIn().setBody((airlineNames != null && airlineNames.size() > 0) ? airlineNames.get(0).getName() :
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
                    .setHeader(HEADER_LOWER_RANGE_ENDPOINT, simple("${bean:dateUtils.format(body.lowerEndpoint())}Z"))
                    .setHeader(HEADER_UPPER_RANGE_ENDPOINT, simple("${bean:dateUtils.format(body.upperEndpoint())}Z"))
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
                    .to("direct:splitJoinIncomingFlightMessages").id("SplitAndJoinRangeSVTFlightsProcessor")
                .end()
        ;

        // @todo: fix some useful wiretapping feature in split
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

        // TODO create unit test for this route
        from("direct:convertCommonDataToNetex")
                .routeId("CommonDataToNetexConverter")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Converting common aviation data to NeTEx")
                .bean(CommonDataToNetexConverter.class, "convertToNetex").id("ConvertCommonDataToNetexProcessor")
                .marshal(jaxbDataFormat)
                //.log(LoggingLevel.DEBUG, this.getClass().getName(), "${body}")
                .process(exchange -> exchange.getIn().setHeader(HEADER_FILE_NAME_GENERATED, "_avinor_common_elements")).id("GenerateCommonFileNameProcessor")
                .setHeader(Exchange.FILE_NAME, simpleF("${header.%s}.xml", HEADER_FILE_NAME_GENERATED))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=utf-8"))
                .setHeader(Exchange.CHARSET_NAME, constant("utf-8"))
                .to("file:{{netex.generated.output.path}}")
        ;

        from("direct:convertScheduledFlightsToNetex")
                .routeId("ScheduledFlightsToNetexConverter")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Converting scheduled flights to NeTEx")

                .split(body())//.parallelProcessing()

                    .process(exchange -> {
                        ScheduledFlight originalBody = exchange.getIn().getBody(ScheduledFlight.class);
                        exchange.setProperty(PROPERTY_SCHEDULED_FLIGHT_ORIGINAL_BODY, originalBody);
                        String enrichParameter = originalBody.getAirlineIATA();
                        exchange.getIn().setBody(enrichParameter);
                    }).id("AirlineIataPreEnrichProcessor")

                    .setHeader(HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT, constant("direct:fetchAndCacheAirlineName"))
                    .enrich("direct:retrieveResource", new AirlineNameEnricherAggregationStrategy())
                    .to("direct:enrichScheduledFlightWithAirportNames")
                    .bean(ScheduledFlightToNetexConverter.class, "convertToNetex").id("ConvertFlightsToNetexProcessor")
                    .process(exchange -> exchange.getIn().setHeader(HEADER_FILE_NAME_GENERATED, generateFilename(exchange))).id("GenerateFileNameProcessor")
                    .marshal(jaxbDataFormat)
                    .setHeader(Exchange.FILE_NAME, simpleF("${header.%s}.xml", HEADER_FILE_NAME_GENERATED))
                    .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=utf-8"))
                    .setHeader(Exchange.CHARSET_NAME, constant("utf-8"))
                    .to("file:{{netex.generated.output.path}}")
                .end()
        ;

        // @TODO: write unit test for this route
        from("direct:enrichScheduledFlightWithAirportNames")
                .routeId("ScheduledFlightAirportNameEnricher")
                .setHeader(HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT, constant("direct:fetchAndCacheAirportName"))

                .choice()
                    .when(body().isInstanceOf(ScheduledDirectFlight.class))
                        .process(new DepartureIataInitProcessor())
                        .enrich("direct:retrieveResource", new DepartureIataEnricherAggregationStrategy())
                        .process(new ArrivalIataInitProcessor())
                        .enrich("direct:retrieveResource", new ArrivalIataEnricherAggregationStrategy())
                    .when(body().isInstanceOf(ScheduledStopoverFlight.class))
                        .to("direct:enrichScheduledStopoverFlightWithAirportNames")
                    .otherwise()
                        .throwException(new IllegalArgumentException("Illegal type argument"))
                .end()
        ;

        from("direct:enrichScheduledStopoverFlightWithAirportNames")
                .routeId("ScheduledStopoverFlightAirportNameEnricher")
                .setHeader(HEADER_STOPOVER_FLIGHT_ORIGINAL_BODY, body())

                .split(simple("${body.scheduledStopovers}"), new StopoverFlightAggregationStrategy())
                    .log("Processing fragment [${property[CamelSplitIndex]}]:${body}")

                    .process(exchange -> {
                        ScheduledStopover originalBody = exchange.getIn().getBody(ScheduledStopover.class);
                        exchange.setProperty(PROPERTY_STOPOVER_ORIGINAL_BODY, originalBody);
                        String enrichParameter = originalBody.getAirportIATA();
                        exchange.getIn().setBody(enrichParameter);
                    }).id("SetEnrichParameterForStopoverProcessor")

                    .enrich("direct:retrieveResource", new StopoverIataEnricherAggregationStrategy())
                .end()
        ;

        // @TODO: write unit test for this route
        from("file:{{netex.generated.output.path}}?delete=true&idempotent=true&antInclude=**/*.xml")
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
                .bean(AvinorTimetableUtils.class, "uploadBlobToStorage").id("UploadZipToBlobStore")
                .log(LoggingLevel.INFO, this.getClass().getName(), "Done storage upload of file : ${header.CamelFileName}")

                .setHeader(HEADER_MESSAGE_PROVIDER_ID, simple("${properties:blobstore.gcs.provider.id}", Long.class))
                .setHeader(HEADER_MESSAGE_FILE_HANDLE, simple("${properties:blobstore.gcs.blob.path}${header.CamelFileName}"))
                .setHeader(HEADER_MESSAGE_FILE_NAME, simple("${header.CamelFileName}"))
                .setBody(constant(null))

                .log(LoggingLevel.INFO, this.getClass().getName(), "Notifying marduk queue about NeTEx export")
                .to("activemq:queue:{{queue.upload.destination.name}}?exchangePattern=InOnly")

                .process(exchange -> {
                    Thread stop = new Thread(() -> {
                        try {
                            exchange.getContext().stopRoute(exchange.getFromRouteId());
                        } catch (Exception ignored) {
                        }
                    });
                    stop.start();
                })
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

    private class StopoverFlightAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                ScheduledStopoverFlight originalBody = newExchange.getIn().getHeader(
                        HEADER_STOPOVER_FLIGHT_ORIGINAL_BODY, ScheduledStopoverFlight.class);
                newExchange.getIn().setBody(originalBody);
                return newExchange;
            } else {
                return oldExchange;
            }
        }
    }

    private class DepartureIataInitProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            ScheduledDirectFlight originalBody = exchange.getIn().getBody(ScheduledDirectFlight.class);
            exchange.setProperty(PROPERTY_DIRECT_FLIGHT_ORIGINAL_BODY, originalBody);
            String enrichParameter = originalBody.getDepartureAirportIATA();
            exchange.getIn().setBody(enrichParameter);
        }
    }

    private class ArrivalIataInitProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            ScheduledDirectFlight originalBody = exchange.getIn().getBody(ScheduledDirectFlight.class);
            exchange.setProperty(PROPERTY_DIRECT_FLIGHT_ORIGINAL_BODY, originalBody);
            String enrichParameter = originalBody.getArrivalAirportIATA();
            exchange.getIn().setBody(enrichParameter);
        }
    }

    private class DepartureIataEnricherAggregationStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange original, Exchange resource) {
            ScheduledDirectFlight originalBody = original.getProperty(
                    PROPERTY_DIRECT_FLIGHT_ORIGINAL_BODY, ScheduledDirectFlight.class);
            String resourceResponse = resource.getIn().getBody(String.class);
            originalBody.setDepartureAirportName(resourceResponse);
            original.getIn().setBody(originalBody);
            return original;
        }
    }

    private class ArrivalIataEnricherAggregationStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange original, Exchange resource) {
            ScheduledDirectFlight originalBody = original.getProperty(
                    PROPERTY_DIRECT_FLIGHT_ORIGINAL_BODY, ScheduledDirectFlight.class);
            String resourceResponse = resource.getIn().getBody(String.class);
            originalBody.setArrivalAirportName(resourceResponse);
            original.getIn().setBody(originalBody);
            return original;
        }
    }

    private class StopoverIataEnricherAggregationStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange original, Exchange resource) {
            ScheduledStopover originalBody = original.getProperty(
                    PROPERTY_STOPOVER_ORIGINAL_BODY, ScheduledStopover.class);
            String resourceResponse = resource.getIn().getBody(String.class);
            originalBody.setAirportName(resourceResponse);
            original.getIn().setBody(originalBody);
            return original;
        }
    }

    private class AirlineNameEnricherAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange original, Exchange resource) {
            ScheduledFlight originalBody = original.getProperty(
                    PROPERTY_SCHEDULED_FLIGHT_ORIGINAL_BODY, ScheduledFlight.class);
            String resourceResponse = resource.getIn().getBody(String.class);
            originalBody.setAirlineName(resourceResponse);
            original.getIn().setBody(originalBody);
            return original;
        }
    }
    
	public static String generateFilename(Exchange exchange) {
		@SuppressWarnings("unchecked")
		JAXBElement<PublicationDeliveryStructure> publicationDelivery = (JAXBElement<PublicationDeliveryStructure>) exchange.getIn().getBody();
		List<ServiceFrame> collect = publicationDelivery.getValue().getDataObjects().getCompositeFrameOrCommonFrame().stream()
				.map(e -> e.getValue())
				.filter(e -> e instanceof CompositeFrame)
				.map(e -> (CompositeFrame)e)
				.map(e -> e.getFrames().getCommonFrame())
				.map(e -> e.stream()
						.map(ex -> ex.getValue())
						.filter(ex -> ex instanceof ServiceFrame)
						.map(ex -> (ServiceFrame)ex)
						.collect(Collectors.toList())
					
				).flatMap(e -> e.stream())
				.collect(Collectors.toList());

		ServiceFrame sf = collect.get(0);
		String network = sf.getNetwork().getName().getValue();
		Line line = ((Line)sf.getLines().getLine_().get(0).getValue());
		String publicCode = line.getPublicCode();
		
		String filename = network+"-"+publicCode+"-"+line.getName().getValue().replace('/', '_');
		
		return filename;
    }
}
