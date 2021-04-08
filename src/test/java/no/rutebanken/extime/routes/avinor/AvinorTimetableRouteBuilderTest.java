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
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
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
class AvinorTimetableRouteBuilderTest extends ExtimeCamelRouteBuilderIntegrationTestBase {

    @EndpointInject("mock:direct:fetchAndCacheAirportName")
    protected MockEndpoint mockFetchAndCacheAirportName;

    @EndpointInject("mock:fetchTimetable")
    protected MockEndpoint mockFetchTimetable;

    @EndpointInject("mock:convertToScheduledFlights")
    protected MockEndpoint mockConvertToScheduledFlights;

    @EndpointInject("mock:direct:convertScheduledFlightsToNetex")
    protected MockEndpoint mockConvertScheduledFlightsToNetex;

    @EndpointInject("mock:fetchXmlFromHttp")
    protected MockEndpoint mockFetchXmlFromHttp;

    @EndpointInject("mock:direct:addResourceToCache")
    protected MockEndpoint mockAddResourceToCache;

    @EndpointInject("mock:largeAirportLogger")
    protected MockEndpoint mockLargeAirportLogger;

    @EndpointInject("mock:smallAirportLogger")
    protected MockEndpoint mockSmallAirportLogger;

    @EndpointInject("mock:direct:fetchTimetableForAirportByRanges")
    protected MockEndpoint mockFetchTimetableForAirportByRanges;

    @EndpointInject("mock:direct:fetchTimetableForLargeAirport")
    protected MockEndpoint mockFetchTimetableForLargeAirport;

    @EndpointInject("mock:fetchAirportFlights")
    protected MockEndpoint mockFetchAirportFlights;

    @EndpointInject("mock:direct:fetchXmlStreamFromHttpFeed")
    protected MockEndpoint mockFetchXmlStreamFromHttpFeed;

    @EndpointInject("mock:splitAndJoinEndpoint")
    protected MockEndpoint mockSplitAndJoinEndpoint;

    @EndpointInject("mock:flightSplitWireTap")
    protected MockEndpoint mockFlightSplitWireTap;

    @EndpointInject("mock:airlineIataPreProcess")
    protected MockEndpoint mockAirlineIataPreProcess;

    @EndpointInject("mock:direct:retrieveAirlineNameResource")
    protected MockEndpoint mockRetrieveAirlineNameResource;

    @EndpointInject("mock:direct:enrichScheduledFlightWithAirportNames")
    protected MockEndpoint mockEnrichScheduledFlightWithAirportNames;

    @EndpointInject("mock:convertToNetex")
    protected MockEndpoint mockConvertToNetex;

    @EndpointInject("mock:generateFileName")
    protected MockEndpoint mockGenerateFileName;

    @EndpointInject("mock:file:target/netex")
    protected MockEndpoint mockFileTargetNetex;
    


    @Produce("direct:fetchAndCacheAirportName")
    protected ProducerTemplate fetchAndCacheAirportNameTemplate;

    @Produce("direct:fetchAndCacheAirlineName")
    protected ProducerTemplate fetchAndCacheAirlineNameTemplate;

    @Produce("direct:fetchTimetableForAirport")
    protected ProducerTemplate fetchTimetableForAirportTemplate;

    @Produce("direct:fetchAirportFlightsByRangeAndStopVisitType")
    protected ProducerTemplate fetchAirportFlightsByRangeAndStopVisitTypeTemplate;

    @Produce("direct:convertScheduledFlightsToNetex")
    protected ProducerTemplate convertScheduledFlightsToNetexTemplate;

    @Produce("direct:fetchTimetableForAirportByRanges")
    protected ProducerTemplate fetchTimetableByRangesTemplate;

    @Produce("direct:splitJoinIncomingFlightMessages")
    protected ProducerTemplate splitJoinFlightsTemplate;


    @Test
    @Disabled // TODO fix test
    void testTimetableScheduler() throws Exception {

        AdviceWith.adviceWith(context, "AvinorTimetableSchedulerStarter", a -> {
            a.weaveById("TimetableAirportIATAProcessor").replace().to("mock:setupIataCodes");
            a.interceptSendToEndpoint("mock:setupIataCodes").process(exchange -> {
                exchange.getIn().setBody(new AirportIATA[]{AirportIATA.OSL, AirportIATA.BGO, AirportIATA.EVE});
            });
            a.weaveById("TimetableDateRangeProcessor").replace().to("mock:setupDateRanges");
            a.mockEndpointsAndSkip("direct:fetchAndCacheAirportName");
            a.weaveById("FetchTimetableProcessor").replace().to("mock:fetchTimetable");
            a.interceptSendToEndpoint("mock:fetchTimetable").process(exchange -> {
                exchange.getIn().setBody(createDummyFlights());
            });
            a.weaveById("ConvertToLineDataSetsBeanProcessor").replace().to("mock:convertToScheduledFlights");
            a.mockEndpointsAndSkip("direct:convertScheduledFlightsToNetex");
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
    void testFetchAndCacheAirportName() throws Exception {

        AdviceWith.adviceWith(context, "FetchAndCacheAirportName", a -> {
            a.weaveById("FetchAirportNameFromHttpFeedProcessor").replace().to("mock:fetchXmlFromHttp");
            a.interceptSendToEndpoint("mock:fetchXmlFromHttp").process(exchange -> {
                InputStream inputStream = new FileInputStream("target/classes/xml/airportname-osl.xml");
                exchange.getIn().setBody(inputStream);
            });
            a.mockEndpointsAndSkip("direct:addResourceToCache");
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
    void testFetchAndCacheAirlineName() throws Exception {

        AdviceWith.adviceWith(context, "FetchAndCacheAirlineName", a -> {
            a.weaveById("FetchAirlineNameFromHttpFeedProcessor").replace().to("mock:fetchXmlFromHttp");
            a.interceptSendToEndpoint("mock:fetchXmlFromHttp").process(exchange -> {
                InputStream inputStream = new FileInputStream("target/classes/xml/airlinename-dy.xml");
                exchange.getIn().setBody(inputStream);
            });
            a.mockEndpointsAndSkip("direct:addResourceToCache");
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
    void testFetchTimetableForLargeAirport() throws Exception {

        AdviceWith.adviceWith(context, "FetchTimetableForAirport", a -> {
            a.weaveById("LargeAirportLogProcessor").replace().to("mock:largeAirportLogger");
            a.mockEndpointsAndSkip("direct:fetchTimetableForAirportByRanges");
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
    void testFetchTimetableForSmallAirport() throws Exception {

        AdviceWith.adviceWith(context, "FetchTimetableForAirport", a -> {
            a.weaveById("SmallAirportLogProcessor").replace().to("mock:smallAirportLogger");
            a.mockEndpointsAndSkip(
                    "direct:fetchTimetableForLargeAirport",
                    "direct:fetchTimetableForAirportByRanges"
            );
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
    void testFetchTimetableForAirportByRanges() throws Exception {

        AdviceWith.adviceWith(context, "FetchTimetableForAirportByDateRanges", a -> {
            a.weaveById("FetchFlightsByRangeAndStopVisitTypeProcessor").replace().to("mock:fetchAirportFlights");
            a.interceptSendToEndpoint("mock:fetchAirportFlights").process(exchange -> {
                exchange.getIn().setBody(createDummyFlights());
            });
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
    void testFetchAirportFlightsByRangeAndStopVisitType() throws Exception {

        AdviceWith.adviceWith(context, "FetchFlightsByRangeAndStopVisitType", a -> {
            a.mockEndpointsAndSkip("direct:fetchXmlStreamFromHttpFeed");
            a.weaveById("SplitAndJoinRangeSVTFlightsProcessor").replace().to("mock:splitAndJoinEndpoint");
            a.interceptSendToEndpoint("mock:splitAndJoinEndpoint").process(exchange -> exchange.getIn().setBody(createDummyFlights()));
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
    void testSplitAndAggregateDepartureFlights() throws Exception {

        AdviceWith.adviceWith(context, "FlightSplitterJoiner", a -> {
            a.weaveById("FlightSplitWireTap").replace().to("mock:flightSplitWireTap");
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
    void testSplitAndAggregateArrivalFlights() throws Exception {

        AdviceWith.adviceWith(context, "FlightSplitterJoiner", a -> {
            a.weaveById("FlightSplitWireTap").replace().to("mock:flightSplitWireTap");
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
    @Disabled
    void testConvertScheduledFlightsToNetex() throws Exception {

        AdviceWith.adviceWith(context, "LineDataSetsToNetexConverter", a -> {
                    a.weaveById("AirlineIataPreEnrichProcessor").replace().to("mock:airlineIataPreProcess");
                    a.weaveById("ConvertLineDataSetsToNetexProcessor").replace().to("mock:convertToNetex");
                    a.interceptSendToEndpoint("mock:convertToNetex").process(exchange -> exchange.getIn().setBody(createPublicationDeliveryElement()));
                    a.weaveById("GenerateFileNameProcessor").replace().to("mock:generateFileName");
                    a.interceptSendToEndpoint("mock:generateFileName").process(
                            exchange -> exchange.getIn().setHeader("FileNameGenerated", "067e6162-3b6f-4ae2-a171-2470b63dff00"));
                    a.mockEndpointsAndSkip(
                            "direct:retrieveAirlineNameResource",
                            "direct:enrichScheduledFlightWithAirportNames",
                            "file:target/netex"
                    );
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
        Flight flight = new Flight();
        flight.setId(BigInteger.valueOf(dummyId));
        flight.setAirlineDesignator(dummyDesignator);
        flight.setFlightNumber(dummyFlightNumber);
        flight.setDateOfOperation(dummyDateOfOperation);
        flight.setDepartureStation(dummyDepartureStation);
        flight.setStd(dummyDepartureTime);
        flight.setArrivalStation(dummyArrivalStation);
        flight.setSta(dummyArrivalTime);

        return flight;
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