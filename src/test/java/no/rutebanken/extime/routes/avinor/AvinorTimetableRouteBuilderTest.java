package no.rutebanken.extime.routes.avinor;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.ExtimeCamelRouteBuilderIntegrationTestBase;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.ScheduledFlight;
import no.rutebanken.extime.model.StopVisitType;
import no.rutebanken.extime.util.DateUtils;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.springframework.boot.test.context.SpringBootTest;

import javax.xml.bind.JAXBElement;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_HTTP_URI;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_RESOURCE_CODE;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_URI_PARAMETERS;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_LOWER_RANGE_ENDPOINT;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_TIMETABLE_LARGE_AIRPORT_RANGE;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_TIMETABLE_SMALL_AIRPORT_RANGE;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_TIMETABLE_STOP_VISIT_TYPE;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_UPPER_RANGE_ENDPOINT;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {AvinorTimetableRouteBuilder.class} , properties = {
        "spring.config.name=application,netex-static-data",
        "avinor.timetable.scheduler.consumer=direct:start",
        "avinor.timetable.period.months=4",
        "avinor.timetable.max.range=180",
        "avinor.timetable.min.range=60",
        "avinor.timetable.feed.endpoint=mock:timetableFeedEndpoint",
        "avinor.airport.feed.endpoint=mock:airportFeedEndpoint",
        "avinor.airports.small=EVE,KRS,MOL,SOG,TOS",
        "avinor.airports.large=BGO,BOO,SVG,TRD",
        "avinor.airline.feed.endpoint=mock:airlineFeedEndpoint",
        "netex.generated.output.path=target/netex-mock",
        "netex.compressed.output.path=target/marduk-mock",
        "queue.upload.destination.name=MockMardukQueue",
        "avinor.timetable.dump.enabled=false",
        "avinor.timetable.dump.output.path=target/flights"
} )
public class AvinorTimetableRouteBuilderTest extends ExtimeCamelRouteBuilderIntegrationTestBase {

    @EndpointInject(uri = "mock:direct:fetchAndCacheAirportName")
    protected MockEndpoint mockFetchAndCacheAirportName;

    @EndpointInject(uri = "mock:fetchTimetable")
    protected MockEndpoint mockFetchTimetable;

    @EndpointInject(uri = "mock:convertToScheduledFlights")
    protected MockEndpoint mockConvertToScheduledFlights;

    @EndpointInject(uri = "mock:direct:convertScheduledFlightsToNetex")
    protected MockEndpoint mockConvertScheduledFlightsToNetex;

    @EndpointInject(uri = "mock:fetchXmlFromHttp")
    protected MockEndpoint mockFetchXmlFromHttp;

    @EndpointInject(uri = "mock:direct:addResourceToCache")
    protected MockEndpoint mockAddResourceToCache;

    @EndpointInject(uri = "mock:largeAirportLogger")
    protected MockEndpoint mockLargeAirportLogger;

    @EndpointInject(uri = "mock:smallAirportLogger")
    protected MockEndpoint mockSmallAirportLogger;

    @EndpointInject(uri = "mock:direct:fetchTimetableForAirportByRanges")
    protected MockEndpoint mockFetchTimetableForAirportByRanges;

    @EndpointInject(uri = "mock:direct:fetchTimetableForLargeAirport")
    protected MockEndpoint mockFetchTimetableForLargeAirport;

    @EndpointInject(uri = "mock:fetchAirportFlights")
    protected MockEndpoint mockFetchAirportFlights;

    @EndpointInject(uri = "mock:direct:fetchXmlStreamFromHttpFeed")
    protected MockEndpoint mockFetchXmlStreamFromHttpFeed;

    @EndpointInject(uri = "mock:splitAndJoinEndpoint")
    protected MockEndpoint mockSplitAndJoinEndpoint;

    @EndpointInject(uri = "mock:flightSplitWireTap")
    protected MockEndpoint mockFlightSplitWireTap;

    @EndpointInject(uri = "mock:airlineIataPreProcess")
    protected MockEndpoint mockAirlineIataPreProcess;

    @EndpointInject(uri = "mock:direct:retrieveAirlineNameResource")
    protected MockEndpoint mockRetrieveAirlineNameResource;

    @EndpointInject(uri = "mock:direct:enrichScheduledFlightWithAirportNames")
    protected MockEndpoint mockEnrichScheduledFlightWithAirportNames;

    @EndpointInject(uri = "mock:convertToNetex")
    protected MockEndpoint mockConvertToNetex;

    @EndpointInject(uri = "mock:generateFileName")
    protected MockEndpoint mockGenerateFileName;

    @EndpointInject(uri = "mock:file:target/netex")
    protected MockEndpoint mockFileTargetNetex;
    


    @Produce(uri = "direct:fetchAndCacheAirportName")
    protected ProducerTemplate fetchAndCacheAirportNameTemplate;

    @Produce(uri = "direct:fetchAndCacheAirlineName")
    protected ProducerTemplate fetchAndCacheAirlineNameTemplate;

    @Produce(uri = "direct:fetchTimetableForAirport")
    protected ProducerTemplate fetchTimetableForAirportTemplate;

    @Produce(uri = "direct:fetchAirportFlightsByRangeAndStopVisitType")
    protected ProducerTemplate fetchAirportFlightsByRangeAndStopVisitTypeTemplate;

    @Produce(uri = "direct:convertScheduledFlightsToNetex")
    protected ProducerTemplate convertScheduledFlightsToNetexTemplate;

    @Produce(uri = "direct:fetchTimetableForAirportByRanges")
    protected ProducerTemplate fetchTimetableByRangesTemplate;

    @Produce(uri = "direct:splitJoinIncomingFlightMessages")
    protected ProducerTemplate splitJoinFlightsTemplate;


    @Test
    @Ignore // TODO fix test
    public void testTimetableScheduler() throws Exception {
        context.getRouteDefinition("AvinorTimetableSchedulerStarter").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("TimetableAirportIATAProcessor").replace().to("mock:setupIataCodes");
                interceptSendToEndpoint("mock:setupIataCodes").process(exchange -> {
                    exchange.getIn().setBody(new AirportIATA[]{AirportIATA.OSL, AirportIATA.BGO, AirportIATA.EVE});
                });
                weaveById("TimetableDateRangeProcessor").replace().to("mock:setupDateRanges");
                mockEndpointsAndSkip("direct:fetchAndCacheAirportName");
                weaveById("FetchTimetableProcessor").replace().to("mock:fetchTimetable");
                interceptSendToEndpoint("mock:fetchTimetable").process(exchange -> {
                    exchange.getIn().setBody(createDummyFlights());
                });
                weaveById("ConvertToLineDataSetsBeanProcessor").replace().to("mock:convertToScheduledFlights");
                mockEndpointsAndSkip("direct:convertScheduledFlightsToNetex");
            }
        });
        context.start();

        mockFetchAndCacheAirportName.expectedMessageCount(3);
        mockFetchAndCacheAirportName.expectedHeaderValuesReceivedInAnyOrder(
                HEADER_EXTIME_RESOURCE_CODE, "OSL", "BGO", "EVE");
        mockFetchTimetable.expectedMessageCount(3);
        mockConvertToScheduledFlights.expectedMessageCount(1);
        mockConvertScheduledFlightsToNetex.expectedMessageCount(1);

        startTemplate.sendBody(null);

        mockFetchAndCacheAirportName.assertIsSatisfied();
        mockFetchTimetable.assertIsSatisfied();
        mockConvertToScheduledFlights.assertIsSatisfied();
        mockConvertScheduledFlightsToNetex.assertIsSatisfied();

    }

    @Test
    public void testFetchAndCacheAirportName() throws Exception {
        context.getRouteDefinition("FetchAndCacheAirportName").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("FetchAirportNameFromHttpFeedProcessor").replace().to("mock:fetchXmlFromHttp");
                interceptSendToEndpoint("mock:fetchXmlFromHttp").process(exchange -> {
                    InputStream inputStream = new FileInputStream("target/classes/xml/airportname-osl.xml");
                    exchange.getIn().setBody(inputStream);
                });
                mockEndpointsAndSkip("direct:addResourceToCache");
            }
        });
        context.start();

        mockFetchXmlFromHttp.expectedMessageCount(1);
        mockFetchXmlFromHttp.expectedHeaderReceived(HEADER_EXTIME_HTTP_URI, "mock:airportFeedEndpoint");
        mockFetchXmlFromHttp.expectedHeaderReceived(HEADER_EXTIME_URI_PARAMETERS, "airport=OSL&shortname=Y&ukname=Y");
        mockAddResourceToCache.expectedMessageCount(1);
        mockAddResourceToCache.expectedHeaderReceived(HEADER_EXTIME_RESOURCE_CODE, "OSL");

        fetchAndCacheAirportNameTemplate.sendBodyAndHeader(null, HEADER_EXTIME_RESOURCE_CODE, "OSL");

        mockFetchXmlFromHttp.assertIsSatisfied();
        mockAddResourceToCache.assertIsSatisfied();

    }

    @Test
    public void testFetchAndCacheAirlineName() throws Exception {
        context.getRouteDefinition("FetchAndCacheAirlineName").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("FetchAirlineNameFromHttpFeedProcessor").replace().to("mock:fetchXmlFromHttp");
                interceptSendToEndpoint("mock:fetchXmlFromHttp").process(exchange -> {
                    InputStream inputStream = new FileInputStream("target/classes/xml/airlinename-dy.xml");
                    exchange.getIn().setBody(inputStream);
                });
                mockEndpointsAndSkip("direct:addResourceToCache");
            }
        });
        context.start();

        mockFetchXmlFromHttp.expectedMessageCount(1);
        mockFetchXmlFromHttp.expectedHeaderReceived(HEADER_EXTIME_HTTP_URI, "mock:airlineFeedEndpoint");
        mockFetchXmlFromHttp.expectedHeaderReceived(HEADER_EXTIME_URI_PARAMETERS, "airline=DY");
        mockAddResourceToCache.expectedMessageCount(1);
        mockAddResourceToCache.expectedHeaderReceived(HEADER_EXTIME_RESOURCE_CODE, "DY");

        fetchAndCacheAirlineNameTemplate.sendBodyAndHeader(null, HEADER_EXTIME_RESOURCE_CODE, "DY");

        mockFetchXmlFromHttp.assertIsSatisfied();
        mockAddResourceToCache.assertIsSatisfied();

    }

    @Test
    public void testFetchTimetableForLargeAirport() throws Exception {
        context.getRouteDefinition("FetchTimetableForAirport").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("LargeAirportLogProcessor").replace().to("mock:largeAirportLogger");
                mockEndpointsAndSkip("direct:fetchTimetableForAirportByRanges");
            }
        });
        context.start();

        mockLargeAirportLogger.expectedMessageCount(1);
        mockFetchTimetableForAirportByRanges.expectedMessageCount(1);

        List<Range<LocalDate>> ranges = Lists.newArrayList(
                createRange("2017-01-01", "2017-01-14"),
                createRange("2017-01-15", "2017-01-29")
        );

        Map<String, Object> headers = Maps.newHashMap();
        headers.put(HEADER_EXTIME_RESOURCE_CODE, "BGO");
        headers.put(HEADER_TIMETABLE_LARGE_AIRPORT_RANGE, ranges);
        fetchTimetableForAirportTemplate.sendBodyAndHeaders(null, headers);

        mockLargeAirportLogger.assertIsSatisfied();
        mockFetchTimetableForAirportByRanges.assertIsSatisfied();
    }

    @Test
    public void testFetchTimetableForSmallAirport() throws Exception {
        context.getRouteDefinition("FetchTimetableForAirport").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("SmallAirportLogProcessor").replace().to("mock:smallAirportLogger");
                mockEndpointsAndSkip(
                        "direct:fetchTimetableForLargeAirport",
                        "direct:fetchTimetableForAirportByRanges"
                );
            }
        });
        context.start();

        mockFetchTimetableForLargeAirport.expectedMessageCount(0);
        mockSmallAirportLogger.expectedMessageCount(1);
        mockFetchTimetableForAirportByRanges.expectedMessageCount(1);

        Map<String, Object> headers = Maps.newHashMap();
        headers.put(HEADER_EXTIME_RESOURCE_CODE, "EVE");
        headers.put(HEADER_TIMETABLE_SMALL_AIRPORT_RANGE, Lists.newArrayList(createRange("2017-01-01", "2017-01-31")));

        fetchTimetableForAirportTemplate.sendBodyAndHeaders(null, headers);

        mockFetchTimetableForLargeAirport.assertIsSatisfied();
        mockSmallAirportLogger.assertIsSatisfied();
        mockFetchTimetableForAirportByRanges.assertIsSatisfied();


    }

    @Test
    public void testFetchTimetableForAirportByRanges() throws Exception {
        context.getRouteDefinition("FetchTimetableForAirportByDateRanges").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("FetchFlightsByRangeAndStopVisitTypeProcessor").replace().to("mock:fetchAirportFlights");
                interceptSendToEndpoint("mock:fetchAirportFlights").process(exchange -> {
                    exchange.getIn().setBody(createDummyFlights());
                });
            }
        });
        context.start();

        mockFetchAirportFlights.expectedMessageCount(3);
        mockFetchAirportFlights.expectedHeaderReceived(
                HEADER_EXTIME_HTTP_URI, "mock:timetableFeedEndpoint");
        mockFetchAirportFlights.expectedHeaderValuesReceivedInAnyOrder(
                HEADER_LOWER_RANGE_ENDPOINT, "2017-01-01Z", "2017-01-09Z", "2017-01-17Z");
        mockFetchAirportFlights.expectedHeaderValuesReceivedInAnyOrder(
                HEADER_UPPER_RANGE_ENDPOINT, "2017-01-08Z", "2017-01-16Z", "2017-01-24Z");

        List<Range<LocalDate>> ranges = Lists.newArrayList(
                createRange("2017-01-01", "2017-01-08"),
                createRange("2017-01-09", "2017-01-16"),
                createRange("2017-01-17", "2017-01-24")
        );

        List<Flight> resultBody = (List<Flight>) fetchTimetableByRangesTemplate.requestBodyAndHeader(
                ranges, HEADER_EXTIME_RESOURCE_CODE, "BGO");

        mockFetchAirportFlights.assertIsSatisfied();

        Assertions.assertThat(resultBody)
                .isNotNull()
                .isNotEmpty()
                .hasSize(9)
                .hasOnlyElementsOfType(Flight.class);
    }

    @Test
    public void testFetchAirportFlightsByRangeAndStopVisitType() throws Exception {
        context.getRouteDefinition("FetchFlightsByRangeAndStopVisitType").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip("direct:fetchXmlStreamFromHttpFeed");
                weaveById("SplitAndJoinRangeSVTFlightsProcessor").replace().to("mock:splitAndJoinEndpoint");
                interceptSendToEndpoint("mock:splitAndJoinEndpoint").process(exchange -> exchange.getIn().setBody(createDummyFlights()));
            }
        });
        context.start();

        mockFetchXmlStreamFromHttpFeed.expectedMessageCount(2);
        mockFetchXmlStreamFromHttpFeed.expectedHeaderValuesReceivedInAnyOrder(
                HEADER_TIMETABLE_STOP_VISIT_TYPE, StopVisitType.ARRIVAL, StopVisitType.DEPARTURE);
        mockFetchXmlStreamFromHttpFeed.expectedHeaderValuesReceivedInAnyOrder(
                HEADER_EXTIME_URI_PARAMETERS,
                "airport=TRD&direction=A&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z",
                "airport=TRD&direction=D&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z");
        mockSplitAndJoinEndpoint.expectedMessageCount(2);

        Map<String, Object> headers = Maps.newHashMap();
        headers.put(HEADER_EXTIME_RESOURCE_CODE, "TRD");
        headers.put(HEADER_LOWER_RANGE_ENDPOINT, "2017-01-01Z");
        headers.put(HEADER_UPPER_RANGE_ENDPOINT, "2017-01-31Z");

        fetchAirportFlightsByRangeAndStopVisitTypeTemplate.sendBodyAndHeaders( null, headers);

        mockFetchXmlStreamFromHttpFeed.assertIsSatisfied();
        mockSplitAndJoinEndpoint.assertIsSatisfied();
    }

    @Test
    public void testSplitAndAggregateDepartureFlights() throws Exception {
        context.getRouteDefinition("FlightSplitterJoiner").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("FlightSplitWireTap").replace().to("mock:flightSplitWireTap");
            }
        });
        context.start();

        mockFlightSplitWireTap.expectedMessageCount(12);

        InputStream inputStream = new FileInputStream("target/classes/xml/scheduled-flights.xml");
        List<Flight> flights = (List<Flight>) splitJoinFlightsTemplate.requestBodyAndHeader(
                inputStream, HEADER_TIMETABLE_STOP_VISIT_TYPE, StopVisitType.DEPARTURE);

        mockFlightSplitWireTap.assertIsSatisfied();

        Assertions.assertThat(flights)
                .isNotNull()
                .isNotEmpty()
                .hasSize(8)
                .hasOnlyElementsOfType(Flight.class);
    }

    @Test
    public void testSplitAndAggregateArrivalFlights() throws Exception {
        context.getRouteDefinition("FlightSplitterJoiner").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("FlightSplitWireTap").replace().to("mock:flightSplitWireTap");
            }
        });
        context.start();

        mockFlightSplitWireTap.expectedMessageCount(24);

        InputStream inputStream = new FileInputStream("target/classes/xml/scheduled-arrivals-trd.xml");
        List<Flight> flights = (List<Flight>) splitJoinFlightsTemplate.requestBodyAndHeader(
                inputStream, HEADER_TIMETABLE_STOP_VISIT_TYPE, StopVisitType.ARRIVAL);

        mockFlightSplitWireTap.assertIsSatisfied();

        Assertions.assertThat(flights)
                .isNotNull()
                .isNotEmpty()
                .hasSize(6)
                .hasOnlyElementsOfType(Flight.class)
                .allMatch((Predicate<Flight>) flight ->
                        flight.getDepartureStation().equals("OSL") && flight.getArrivalStation().equals("TRD"));
    }

    @Test
    @Ignore
    public void testConvertScheduledFlightsToNetex() throws Exception {
        context.getRouteDefinition("LineDataSetsToNetexConverter").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("AirlineIataPreEnrichProcessor").replace().to("mock:airlineIataPreProcess");
                weaveById("ConvertLineDataSetsToNetexProcessor").replace().to("mock:convertToNetex");
                interceptSendToEndpoint("mock:convertToNetex").process(exchange -> exchange.getIn().setBody(createPublicationDeliveryElement()));
                weaveById("GenerateFileNameProcessor").replace().to("mock:generateFileName");
                interceptSendToEndpoint("mock:generateFileName").process(
                        exchange -> exchange.getIn().setHeader("FileNameGenerated", "067e6162-3b6f-4ae2-a171-2470b63dff00"));
                mockEndpointsAndSkip(
                        "direct:retrieveAirlineNameResource",
                        "direct:enrichScheduledFlightWithAirportNames",
                        "file:target/netex"
                );
            }
        });
        context.start();

        mockAirlineIataPreProcess.expectedMessageCount(2);
        mockRetrieveAirlineNameResource.expectedMessageCount(2);
        mockEnrichScheduledFlightWithAirportNames.expectedMessageCount(2);
        mockConvertToNetex.expectedMessageCount(2);
        mockGenerateFileName.expectedMessageCount(2);

        mockFileTargetNetex.expectedMessageCount(2);
        mockFileTargetNetex.expectedHeaderReceived(Exchange.FILE_NAME, "067e6162-3b6f-4ae2-a171-2470b63dff00.xml");
        mockFileTargetNetex.expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/xml;charset=utf-8");
        mockFileTargetNetex.expectedHeaderReceived(Exchange.CHARSET_NAME, "utf-8");
        mockFileTargetNetex.expectedBodiesReceived(createPublicationDelivery(), createPublicationDelivery());

        List<ScheduledFlight> scheduledFlights = Lists.newArrayList(
                createScheduledFlight("WF", "WF739", LocalDate.now()),
                createScheduledFlight("SK", "SK1038", LocalDate.now())
        );

        convertScheduledFlightsToNetexTemplate.sendBody(scheduledFlights);

        mockAirlineIataPreProcess.assertIsSatisfied();
        mockRetrieveAirlineNameResource.assertIsSatisfied();
        mockEnrichScheduledFlightWithAirportNames.assertIsSatisfied();
        mockConvertToNetex.assertIsSatisfied();
        mockGenerateFileName.assertIsSatisfied();
        mockFileTargetNetex.assertIsSatisfied();

    }

    private Range<LocalDate> createRange(String lower, String upper) {
        return Range.closed(LocalDate.parse(lower), LocalDate.parse(upper));
    }

    private List<Flight> createDummyFlights() {
        return Lists.newArrayList(
                createDummyFlight(1L, "SK", "4455", DateUtils.parseDate("2017-01-01"), "BGO", LocalTime.of(0, 0), "OSL", LocalTime.of(23, 59)),
                createDummyFlight(2L, "DY", "6677", DateUtils.parseDate("2017-01-02"), "BGO", LocalTime.of(0, 0), "TRD", LocalTime.of(23, 59)),
                createDummyFlight(3L, "WF", "199", DateUtils.parseDate("2017-01-03"), "BGO", LocalTime.of(0, 0), "SVG", LocalTime.of(23, 59))
        );
    }

    private ScheduledFlight createScheduledFlight(String airlineIATA, String airlineFlightId, LocalDate dateOfOperation) {
        ScheduledFlight scheduledFlight = new ScheduledFlight();
        scheduledFlight.setAirlineIATA(airlineIATA);
        scheduledFlight.setAirlineFlightId(airlineFlightId);
        scheduledFlight.setDateOfOperation(dateOfOperation);
        scheduledFlight.setDepartureAirportIATA("");
        scheduledFlight.setDepartureAirportName("");
        scheduledFlight.setArrivalAirportIATA("");
        scheduledFlight.setArrivalAirportName("");
        scheduledFlight.setTimeOfDeparture(LocalTime.of(0, 0));
        scheduledFlight.setTimeOfArrival(LocalTime.of(23, 59));
        return scheduledFlight;
    }

    private Flight createDummyFlight(long dummyId, String dummyDesignator, String dummyFlightNumber, ZonedDateTime dummyDateOfOperation,
                                     String dummyDepartureStation, LocalTime dummyDepartureTime, String dummyArrivalStation, LocalTime dummyArrivalTime) {
        return new Flight() {{
            setId(BigInteger.valueOf(dummyId));
            setAirlineDesignator(dummyDesignator);
            setFlightNumber(dummyFlightNumber);
            setDateOfOperation(dummyDateOfOperation);
            setDepartureStation(dummyDepartureStation);
            setStd(dummyDepartureTime);
            setArrivalStation(dummyArrivalStation);
            setSta(dummyArrivalTime);
        }};
    }


    private JAXBElement<PublicationDeliveryStructure> createPublicationDeliveryElement() {
        ObjectFactory objectFactory = new ObjectFactory();
        PublicationDeliveryStructure publicationDeliveryStructure1 = objectFactory.createPublicationDeliveryStructure()
                .withVersion("1.0")
                .withPublicationTimestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2016-08-16T08:24:21Z", LocalDateTime::from))
                .withParticipantRef("AVI");
        return objectFactory.createPublicationDelivery(publicationDeliveryStructure1);
    }

    private PublicationDeliveryStructure createPublicationDelivery() {
        ObjectFactory objectFactory = new ObjectFactory();
        return objectFactory.createPublicationDeliveryStructure()
                .withVersion("1.0")
                .withPublicationTimestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2016-08-16T08:24:21Z", LocalDateTime::from))
                .withParticipantRef("AVI");
    }

}