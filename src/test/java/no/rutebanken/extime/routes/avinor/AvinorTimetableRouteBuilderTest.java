package no.rutebanken.extime.routes.avinor;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.model.*;
import no.rutebanken.extime.util.DateUtils;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.DefaultPropertiesParser;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jndi.JndiContext;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.*;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.*;

@SuppressWarnings("unchecked")
public class AvinorTimetableRouteBuilderTest extends CamelTestSupport {

    @Produce(uri = "direct:fetchAirportNameFromFeed")
    private ProducerTemplate fetchAirportNameTemplate;

    @Produce(uri = "direct:fetchAirlineNameFromFeed")
    private ProducerTemplate fetchAirlineNameTemplate;

    @Produce(uri = "direct:fetchTimetableForLargeAirport")
    private ProducerTemplate fetchTimetableForLargeAirportTemplate;

    @Produce(uri = "direct:fetchTimetableForAirportByRanges")
    private ProducerTemplate fetchTimetableByRangesTemplate;

    @Produce(uri = "direct:splitJoinIncomingFlightMessages")
    private ProducerTemplate splitJoinFlightsTemplate;

    @Produce(uri = "direct:enrichScheduledStopoverFlightWithAirportNames")
    private ProducerTemplate enrichStopoverFlightTemplate;

    @Produce(uri = "direct:retrieveAirportNamesForStopovers")
    private ProducerTemplate enrichStopoversTemplate;

    DateUtils dateUtils = new DateUtils();

    @Test
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
                weaveById("ConvertToScheduledFlightsBeanProcessor").replace().to("mock:convertToScheduledFlights");
                mockEndpointsAndSkip("direct:convertScheduledFlightsToNetex");
            }
        });
        context.start();

        getMockEndpoint("mock:direct:fetchAndCacheAirportName").expectedMessageCount(3);
        getMockEndpoint("mock:direct:fetchAndCacheAirportName").expectedHeaderValuesReceivedInAnyOrder(
                HEADER_EXTIME_RESOURCE_CODE, "OSL", "BGO", "EVE");
        getMockEndpoint("mock:fetchTimetable").expectedMessageCount(3);
        getMockEndpoint("mock:convertToScheduledFlights").expectedMessageCount(1);
        getMockEndpoint("mock:direct:convertScheduledFlightsToNetex").expectedMessageCount(1);

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();
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

        getMockEndpoint("mock:fetchXmlFromHttp").expectedMessageCount(1);
        getMockEndpoint("mock:fetchXmlFromHttp").expectedHeaderReceived(HEADER_EXTIME_HTTP_URI, "mock:airportFeedEndpoint");
        getMockEndpoint("mock:fetchXmlFromHttp").expectedHeaderReceived(HEADER_EXTIME_URI_PARAMETERS, "airport=OSL&shortname=Y&ukname=Y");
        getMockEndpoint("mock:direct:addResourceToCache").expectedMessageCount(1);
        getMockEndpoint("mock:direct:addResourceToCache").expectedHeaderReceived(HEADER_EXTIME_RESOURCE_CODE, "OSL");

        template.sendBodyAndHeader("direct:fetchAndCacheAirportName", null, HEADER_EXTIME_RESOURCE_CODE, "OSL");

        assertMockEndpointsSatisfied();
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

        getMockEndpoint("mock:fetchXmlFromHttp").expectedMessageCount(1);
        getMockEndpoint("mock:fetchXmlFromHttp").expectedHeaderReceived(HEADER_EXTIME_HTTP_URI, "mock:airlineFeedEndpoint");
        getMockEndpoint("mock:fetchXmlFromHttp").expectedHeaderReceived(HEADER_EXTIME_URI_PARAMETERS, "airline=DY");
        getMockEndpoint("mock:direct:addResourceToCache").expectedMessageCount(1);
        getMockEndpoint("mock:direct:addResourceToCache").expectedHeaderReceived(HEADER_EXTIME_RESOURCE_CODE, "DY");

        template.sendBodyAndHeader("direct:fetchAndCacheAirlineName", null, HEADER_EXTIME_RESOURCE_CODE, "DY");

        assertMockEndpointsSatisfied();
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

        getMockEndpoint("mock:largeAirportLogger").expectedMessageCount(1);
        getMockEndpoint("mock:direct:fetchTimetableForAirportByRanges").expectedMessageCount(1);

        List<Range<LocalDate>> ranges = Lists.newArrayList(
                createRange("2017-01-01", "2017-01-14"),
                createRange("2017-01-15", "2017-01-29")
        );

        Map<String,Object> headers = Maps.newHashMap();
        headers.put(HEADER_EXTIME_RESOURCE_CODE, "BGO");
        headers.put(HEADER_TIMETABLE_LARGE_AIRPORT_RANGE, ranges);
        template.sendBodyAndHeaders("direct:fetchTimetableForAirport", null, headers);

        assertMockEndpointsSatisfied();
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

        getMockEndpoint("mock:direct:fetchTimetableForLargeAirport").expectedMessageCount(0);
        getMockEndpoint("mock:smallAirportLogger").expectedMessageCount(1);
        getMockEndpoint("mock:direct:fetchTimetableForAirportByRanges").expectedMessageCount(1);

        Map<String,Object> headers = Maps.newHashMap();
        headers.put(HEADER_EXTIME_RESOURCE_CODE, "EVE");
        headers.put(HEADER_TIMETABLE_SMALL_AIRPORT_RANGE, Lists.newArrayList(createRange("2017-01-01", "2017-01-31")));

        template.sendBodyAndHeaders("direct:fetchTimetableForAirport", null, headers);

        assertMockEndpointsSatisfied();
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

        getMockEndpoint("mock:fetchAirportFlights").expectedMessageCount(3);
        getMockEndpoint("mock:fetchAirportFlights").expectedHeaderReceived(
                HEADER_EXTIME_HTTP_URI, "mock:timetableFeedEndpoint");
        getMockEndpoint("mock:fetchAirportFlights").expectedHeaderValuesReceivedInAnyOrder(
                HEADER_LOWER_RANGE_ENDPOINT, "2017-01-01Z", "2017-01-09Z", "2017-01-17Z");
        getMockEndpoint("mock:fetchAirportFlights").expectedHeaderValuesReceivedInAnyOrder(
                HEADER_UPPER_RANGE_ENDPOINT, "2017-01-08Z", "2017-01-16Z", "2017-01-24Z");

        List<Range<LocalDate>> ranges = Lists.newArrayList(
                createRange("2017-01-01", "2017-01-08"),
                createRange("2017-01-09", "2017-01-16"),
                createRange("2017-01-17", "2017-01-24")
        );

        List<Flight> resultBody = (List<Flight>) fetchTimetableByRangesTemplate.requestBodyAndHeader(
                ranges, HEADER_EXTIME_RESOURCE_CODE, "BGO");

        assertMockEndpointsSatisfied();

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

        getMockEndpoint("mock:direct:fetchXmlStreamFromHttpFeed").expectedMessageCount(2);
        getMockEndpoint("mock:direct:fetchXmlStreamFromHttpFeed").expectedHeaderValuesReceivedInAnyOrder(
                HEADER_TIMETABLE_STOP_VISIT_TYPE, StopVisitType.ARRIVAL, StopVisitType.DEPARTURE);
        getMockEndpoint("mock:direct:fetchXmlStreamFromHttpFeed").expectedHeaderValuesReceivedInAnyOrder(
                HEADER_EXTIME_URI_PARAMETERS,
                "airport=TRD&direction=A&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z",
                "airport=TRD&direction=D&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z");
        getMockEndpoint("mock:splitAndJoinEndpoint").expectedMessageCount(2);

        Map<String,Object> headers = Maps.newHashMap();
        headers.put(HEADER_EXTIME_RESOURCE_CODE, "TRD");
        headers.put(HEADER_LOWER_RANGE_ENDPOINT, "2017-01-01Z");
        headers.put(HEADER_UPPER_RANGE_ENDPOINT, "2017-01-31Z");

        template.sendBodyAndHeaders("direct:fetchAirportFlightsByRangeAndStopVisitType", null, headers);

        assertMockEndpointsSatisfied();
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

        getMockEndpoint("mock:flightSplitWireTap").expectedMessageCount(12);

        InputStream inputStream = new FileInputStream("target/classes/xml/scheduled-flights.xml");
        List<Flight> flights  = (List<Flight>) splitJoinFlightsTemplate.requestBodyAndHeader(
                inputStream, HEADER_TIMETABLE_STOP_VISIT_TYPE, StopVisitType.DEPARTURE);

        assertMockEndpointsSatisfied();

        Assertions.assertThat(flights)
                .isNotNull()
                .isNotEmpty()
                .hasSize(9)
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

        getMockEndpoint("mock:flightSplitWireTap").expectedMessageCount(24);

        InputStream inputStream = new FileInputStream("target/classes/xml/scheduled-arrivals-trd.xml");
        List<Flight> flights  = (List<Flight>) splitJoinFlightsTemplate.requestBodyAndHeader(
                inputStream, HEADER_TIMETABLE_STOP_VISIT_TYPE, StopVisitType.ARRIVAL);

        assertMockEndpointsSatisfied();

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
        context.getRouteDefinition("ScheduledFlightsToNetexConverter").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("AirlineIataPreEnrichProcessor").replace().to("mock:airlineIataPreProcess");
                weaveById("ConvertFlightsToNetexProcessor").replace().to("mock:convertToNetex");
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

        getMockEndpoint("mock:airlineIataPreProcess").expectedMessageCount(2);
        getMockEndpoint("mock:direct:retrieveAirlineNameResource").expectedMessageCount(2);
        getMockEndpoint("mock:direct:enrichScheduledFlightWithAirportNames").expectedMessageCount(2);
        getMockEndpoint("mock:convertToNetex").expectedMessageCount(2);
        getMockEndpoint("mock:generateFileName").expectedMessageCount(2);

        getMockEndpoint("mock:file:target/netex").expectedMessageCount(2);
        getMockEndpoint("mock:file:target/netex").expectedHeaderReceived(Exchange.FILE_NAME, "067e6162-3b6f-4ae2-a171-2470b63dff00.xml");
        getMockEndpoint("mock:file:target/netex").expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/xml;charset=utf-8");
        getMockEndpoint("mock:file:target/netex").expectedHeaderReceived(Exchange.CHARSET_NAME, "utf-8");
        getMockEndpoint("mock:file:target/netex").expectedBodiesReceived(createPublicationDelivery(), createPublicationDelivery());

        List<ScheduledFlight> scheduledFlights = Lists.newArrayList(
                createScheduledFlight("WF", "WF739", LocalDate.now()),
                createScheduledFlight("SK", "SK1038", LocalDate.now())
        );

        template.sendBody("direct:convertScheduledFlightsToNetex", scheduledFlights);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEnrichScheduledStopoverFlightWithAirportNames() throws Exception {
        ScheduledStopoverFlight scheduledStopoverFlight = createScheduledStopoverFlight(
                "WF", "WF149", LocalDate.parse("2016-12-24"), createScheduledStopovers());

        context.getRouteDefinition("ScheduledStopoverFlightAirportNameEnricher").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("SetEnrichParameterForStopoverProcessor").replace().to("mock:setEnrichParameter");
                interceptSendToEndpoint("mock:setEnrichParameter").process(exchange ->
                        exchange.setProperty(PROPERTY_STOPOVER_ORIGINAL_BODY, new ScheduledStopover()));
                mockEndpointsAndSkip("direct:retrieveResource");
                interceptSendToEndpoint("mock:direct:retrieveResource").process(exchange ->
                        exchange.getIn().setBody("TEST-NAME"));
            }
        });
        context.start();

        getMockEndpoint("mock:setEnrichParameter").expectedMessageCount(4);
        getMockEndpoint("mock:setEnrichParameter").expectedHeaderReceived(
                HEADER_STOPOVER_FLIGHT_ORIGINAL_BODY, scheduledStopoverFlight);

        getMockEndpoint("mock:direct:retrieveResource").expectedMessageCount(4);
        getMockEndpoint("mock:setEnrichParameter").expectedHeaderReceived(
                HEADER_STOPOVER_FLIGHT_ORIGINAL_BODY, scheduledStopoverFlight);

        ScheduledStopoverFlight resultBody = (ScheduledStopoverFlight)
                enrichStopoverFlightTemplate.requestBody(scheduledStopoverFlight);

        assertMockEndpointsSatisfied();

        Assertions.assertThat(resultBody)
                .isNotNull();
        Assertions.assertThat(resultBody.getScheduledStopovers())
                .isNotNull()
                .isNotEmpty()
                .hasSize(4);
    }

    private Range<LocalDate> createRange(String lower, String upper) {
        return Range.closed(LocalDate.parse(lower), LocalDate.parse(upper));
    }

    private List<Flight> createDummyFlights() {
        return Lists.newArrayList(
                createDummyFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO", OffsetTime.MIN, "OSL", OffsetTime.MAX),
                createDummyFlight(2L, "DY", "6677", LocalDate.parse("2017-01-02"), "BGO", OffsetTime.MIN, "TRD", OffsetTime.MAX),
                createDummyFlight(3L, "WF", "199", LocalDate.parse("2017-01-03"), "BGO", OffsetTime.MIN, "SVG", OffsetTime.MAX)
        );
    }

    private List<ScheduledStopover> createScheduledStopovers() {
        return Lists.newArrayList(
                createScheduledStopover("OSL", null, OffsetTime.parse("12:15:00Z")),
                createScheduledStopover("HOV", OffsetTime.parse("12:45:00Z"), OffsetTime.parse("13:00:00Z")),
                createScheduledStopover("SOG", OffsetTime.parse("13:30:00Z"), OffsetTime.parse("13:45:00Z")),
                createScheduledStopover("BGO", OffsetTime.parse("14:15:00Z"), null)
        );
    }

    private ScheduledStopover createScheduledStopover(String airportIata, OffsetTime arrivalTime, OffsetTime departureTime) {
        ScheduledStopover scheduledStopover = new ScheduledStopover();
        scheduledStopover.setAirportIATA(airportIata);
        if (arrivalTime != null) {
            scheduledStopover.setArrivalTime(arrivalTime);
        }
        if (departureTime != null) {
            scheduledStopover.setDepartureTime(departureTime);
        }
        return scheduledStopover;
    }

    private ScheduledFlight createScheduledFlight(String airlineIATA, String airlineFlightId, LocalDate dateOfOperation) {
        ScheduledDirectFlight scheduledFlight = new ScheduledDirectFlight();
        scheduledFlight.setAirlineIATA(airlineIATA);
        scheduledFlight.setAirlineFlightId(airlineFlightId);
        scheduledFlight.setDateOfOperation(dateOfOperation);
        scheduledFlight.setDepartureAirportIATA("");
        scheduledFlight.setDepartureAirportName("");
        scheduledFlight.setArrivalAirportIATA("");
        scheduledFlight.setArrivalAirportName("");
        scheduledFlight.setTimeOfDeparture(OffsetTime.MIN);
        scheduledFlight.setTimeOfArrival(OffsetTime.MAX);
        return scheduledFlight;
    }

    private ScheduledStopoverFlight createScheduledStopoverFlight(String airlineIATA, String airlineFlightId,
                                                                  LocalDate dateOfOperation, List<ScheduledStopover> stopovers) {
        ScheduledStopoverFlight stopoverFlight = new ScheduledStopoverFlight();
        stopoverFlight.setAirlineIATA(airlineIATA);
        stopoverFlight.setAirlineFlightId(airlineFlightId);
        stopoverFlight.setDateOfOperation(dateOfOperation);
        stopoverFlight.getScheduledStopovers().addAll(stopovers);
        return stopoverFlight;
    }

    private Flight createDummyFlight(long dummyId, String dummyDesignator, String dummyFlightNumber, LocalDate dummyDateOfOperation,
                                     String dummyDepartureStation, OffsetTime dummyDepartureTime, String dummyArrivalStation, OffsetTime dummyArrivalTime) {
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
                .withPublicationTimestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2016-08-16T08:24:21Z", OffsetDateTime::from))
                .withParticipantRef("AVI");
        return objectFactory.createPublicationDelivery(publicationDeliveryStructure1);
    }

    private PublicationDeliveryStructure createPublicationDelivery() {
        ObjectFactory objectFactory = new ObjectFactory();
        return objectFactory.createPublicationDeliveryStructure()
                .withVersion("1.0")
                .withPublicationTimestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2016-08-16T08:24:21Z", OffsetDateTime::from))
                .withParticipantRef("AVI");
    }

    // @todo: refactor! - duplicate in AvinorTimetableUtils (make static)
    private <T> T generateObjectsFromXml(String resourceName, Class<T> clazz) throws JAXBException {
        return JAXBContext.newInstance(clazz).createUnmarshaller().unmarshal(
                new StreamSource(getClass().getResourceAsStream(resourceName)), clazz).getValue();
    }

    @Override
    public JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndiRegistry = new JndiRegistry(new JndiContext());
        jndiRegistry.bind("dateUtils", dateUtils);
        return jndiRegistry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        PropertiesComponent pc = (PropertiesComponent) context.getComponent("properties");
        pc.setPropertiesParser(new DefaultPropertiesParser() {
            @Override
            public String parseProperty(String key, String value, Properties properties) {
                Map<String, String> testProperties = new HashMap<String, String>() {{
                    put("avinor.timetable.scheduler.consumer", "direct:start");
                    put("avinor.timetable.period.months", "4");
                    put("avinor.timetable.max.range", "180");
                    put("avinor.timetable.min.range", "60");
                    put("avinor.timetable.feed.endpoint", "mock:timetableFeedEndpoint");
                    put("avinor.airport.feed.endpoint", "mock:airportFeedEndpoint");
                    put("avinor.airports.small", "EVE,KRS,MOL,SOG,TOS");
                    put("avinor.airports.large", "BGO,BOO,SVG,TRD");
                    put("avinor.airline.feed.endpoint", "mock:airlineFeedEndpoint");
                }};
                return testProperties.get(key);
            }
        });
        return new AvinorTimetableRouteBuilder();
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    public boolean isUseDebugger() {
        return true;
    }

    @Override
    protected void debugBefore(Exchange exchange, Processor processor,
                               ProcessorDefinition<?> definition, String id, String shortName) {
        log.info("Before " + definition + " with body " + exchange.getIn().getBody());
    }

    @Override
    protected void debugAfter(Exchange exchange, Processor processor,
                              ProcessorDefinition<?> definition, String id, String label, long timeTaken) {
        log.info("After " + definition + " with body " + exchange.getIn().getBody());
    }

}