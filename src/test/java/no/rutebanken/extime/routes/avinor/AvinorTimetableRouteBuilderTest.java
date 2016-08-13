package no.rutebanken.extime.routes.avinor;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.ScheduledStopover;
import no.rutebanken.extime.model.ScheduledStopoverFlight;
import no.rutebanken.extime.model.StopVisitType;
import no.rutebanken.extime.util.DateUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cache.CacheConstants;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.component.properties.DefaultPropertiesParser;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jndi.JndiContext;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.*;

@SuppressWarnings("unchecked")
public class AvinorTimetableRouteBuilderTest extends CamelTestSupport {

    @Produce(uri = "direct:fetchAirportNameFromFeed")
    private ProducerTemplate fetchAirportNameTemplate;

    @Produce(uri = "direct:fetchTimetableForLargeAirport")
    private ProducerTemplate fetchTimetableForLargeAirportTemplate;

    @Produce(uri = "direct:fetchTimetableForAirportByRanges")
    private ProducerTemplate fetchTimetableByRangesTemplate;

    @Produce(uri = "direct:splitJoinIncomingFlightMessages")
    private ProducerTemplate splitJoinFlightsTemplate;

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
                mockEndpointsAndSkip("direct:fetchAirportNameByIATA");
                weaveById("FetchTimetableProcessor").replace().to("mock:fetchTimetable");
                interceptSendToEndpoint("mock:fetchTimetable").process(exchange -> {
                    exchange.getIn().setBody(createDummyFlights());
                });
            }
        });
        context.start();

        getMockEndpoint("mock:direct:fetchAirportNameByIATA").expectedMessageCount(3);
        getMockEndpoint("mock:direct:fetchAirportNameByIATA").expectedHeaderValuesReceivedInAnyOrder(
                HEADER_TIMETABLE_AIRPORT_IATA, "OSL", "BGO", "EVE");
        getMockEndpoint("mock:fetchTimetable").expectedMessageCount(3);
        getMockEndpoint("mock:direct:convertToDirectFlights").expectedMessageCount(1);
        getMockEndpoint("mock:direct:convertToStopoverFlights").expectedMessageCount(1);

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFetchAirportNameByIataWhenInCache() throws Exception {
        context.getRouteDefinition("FetchAirportNameByIata").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("TimetableCacheCheckProcessor").replace().to("mock:cacheCheck");
                interceptSendToEndpoint("mock:cacheCheck").process(exchange -> {
                    exchange.getIn().setHeader(CacheConstants.CACHE_ELEMENT_WAS_FOUND, 1);
                });
                mockEndpointsAndSkip(
                        "direct:fetchAirportNameFromFeed",
                        "direct:addAirportNameToCache"
                );
            }
        });
        context.start();

        getMockEndpoint("mock:cacheCheck").expectedMessageCount(1);
        getMockEndpoint("mock:cacheCheck").expectedHeaderReceived(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_CHECK);
        getMockEndpoint("mock:cacheCheck").expectedHeaderReceived(CacheConstants.CACHE_KEY, "OSL");
        getMockEndpoint("mock:direct:fetchAirportNameFromFeed").expectedMessageCount(0);
        getMockEndpoint("mock:direct:addAirportNameToCache").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:fetchAirportNameByIATA", null, HEADER_TIMETABLE_AIRPORT_IATA, "OSL");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFetchAirportNameByIataWhenNotInCache() throws Exception {
        context.getRouteDefinition("FetchAirportNameByIata").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("TimetableCacheCheckProcessor").replace().to("mock:cacheCheck");
                interceptSendToEndpoint("mock:cacheCheck").process(exchange -> {
                    exchange.getIn().setHeader(CacheConstants.CACHE_ELEMENT_WAS_FOUND, null);
                });
                mockEndpointsAndSkip(
                        "direct:fetchAirportNameFromFeed",
                        "direct:addAirportNameToCache"
                );
            }
        });
        context.start();

        getMockEndpoint("mock:cacheCheck").expectedMessageCount(1);
        getMockEndpoint("mock:cacheCheck").expectedHeaderReceived(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_CHECK);
        getMockEndpoint("mock:cacheCheck").expectedHeaderReceived(CacheConstants.CACHE_KEY, "OSL");
        getMockEndpoint("mock:direct:fetchAirportNameFromFeed").expectedMessageCount(1);
        getMockEndpoint("mock:direct:addAirportNameToCache").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:fetchAirportNameByIATA", null, HEADER_TIMETABLE_AIRPORT_IATA, "OSL");

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
        headers.put(HEADER_TIMETABLE_AIRPORT_IATA, "BGO");
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
        headers.put(HEADER_TIMETABLE_AIRPORT_IATA, "EVE");
        headers.put(HEADER_TIMETABLE_SMALL_AIRPORT_RANGE, Lists.newArrayList(createRange("2017-01-01", "2017-01-31")));

        template.sendBodyAndHeaders("direct:fetchTimetableForAirport", null, headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFetchAirportNameFromFeed() throws Exception {
        context.getRouteDefinition("FetchAirportNameFromFeed").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:airportFeedEndpoint").process(exchange -> {
                    InputStream inputStream = new FileInputStream("target/classes/xml/airportname-osl.xml");
                    exchange.getIn().setBody(inputStream);
                });
            }
        });
        context.start();

        getMockEndpoint("mock:airportFeedEndpoint").expectedMessageCount(1);
        getMockEndpoint("mock:airportFeedEndpoint").expectedHeaderReceived(Exchange.HTTP_METHOD, HttpMethods.GET);
        getMockEndpoint("mock:airportFeedEndpoint").expectedHeaderReceived(Exchange.HTTP_QUERY, "airport=OSL&shortname=Y&ukname=Y");

        String airportName = (String) fetchAirportNameTemplate.requestBodyAndHeader("OSL", HEADER_TIMETABLE_AIRPORT_IATA, "OSL");

        assertMockEndpointsSatisfied();
        Assertions.assertThat(airportName)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("Oslo");
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
                ranges, HEADER_TIMETABLE_AIRPORT_IATA, "BGO");

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
                mockEndpointsAndSkip("direct:fetchFromHttpResource");
                weaveById("SplitAndJoinRangeSVTFlightsProcessor").replace().to("mock:splitAndJoinEndpoint");
                interceptSendToEndpoint("mock:splitAndJoinEndpoint").process(exchange -> {
                    exchange.getIn().setBody(createDummyFlights());
                });
            }
        });
        context.start();

        getMockEndpoint("mock:direct:fetchFromHttpResource").expectedMessageCount(2);
        getMockEndpoint("mock:direct:fetchFromHttpResource").expectedHeaderValuesReceivedInAnyOrder(
                HEADER_TIMETABLE_STOP_VISIT_TYPE, StopVisitType.ARRIVAL, StopVisitType.DEPARTURE);
        getMockEndpoint("mock:direct:fetchFromHttpResource").expectedHeaderValuesReceivedInAnyOrder(
                HEADER_EXTIME_URI_PARAMETERS,
                "airport=TRD&direction=A&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z",
                "airport=TRD&direction=D&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z");
        getMockEndpoint("mock:splitAndJoinEndpoint").expectedMessageCount(2);

        Map<String,Object> headers = Maps.newHashMap();
        headers.put(HEADER_TIMETABLE_AIRPORT_IATA, "TRD");
        headers.put(HEADER_LOWER_RANGE_ENDPOINT, "2017-01-01Z");
        headers.put(HEADER_UPPER_RANGE_ENDPOINT, "2017-01-31Z");

        template.sendBodyAndHeaders("direct:fetchAirportFlightsByRangeAndStopVisitType", null, headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFetchFromHttpResource() throws Exception {
        context.getRouteDefinition("FetchFromHttpResource").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("FetchFromHttpResourceProcessor").replace().to("mock:fetchFromHttpResource");
            }
        });
        context.start();

        getMockEndpoint("mock:fetchFromHttpResource").expectedMessageCount(1);
        getMockEndpoint("mock:fetchFromHttpResource").expectedHeaderReceived(
                Exchange.HTTP_METHOD, HttpMethods.GET);
        getMockEndpoint("mock:fetchFromHttpResource").expectedHeaderReceived(
                Exchange.HTTP_QUERY, "airport=TRD&direction=D&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z");
        getMockEndpoint("mock:fetchFromHttpResource").expectedHeaderReceived(
                HEADER_EXTIME_HTTP_URI, "http4://flydata.avinor.no/airportNames.asp");

        Map<String,Object> headers = Maps.newHashMap();
        headers.put(HEADER_EXTIME_HTTP_URI, "http://flydata.avinor.no/airportNames.asp");
        headers.put(HEADER_EXTIME_URI_PARAMETERS, "airport=TRD&direction=D&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z");

        template.sendBodyAndHeaders("direct:fetchFromHttpResource", null, headers);

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
    public void testRetrieveAirportNamesForStopovers() throws Exception {
        context.getRouteDefinition("StopoverFlightAirportNameEnricher").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("CacheGetAirportNameForStopoverProcessor").replace().to("mock:cacheGet");
                interceptSendToEndpoint("mock:cacheGet").process(exchange -> {
                    exchange.getIn().setBody("DUMMY-AIRPORT-NAME");
                });
            }
        });
        context.start();

        List<ScheduledStopover> scheduledStopovers = Lists.newArrayList(
                createDummyScheduledStopover(1L, "OSL"),
                createDummyScheduledStopover(2L, "BGO"),
                createDummyScheduledStopover(3L, "TRD")
        );

        getMockEndpoint("mock:cacheGet").expectedMessageCount(3);
        getMockEndpoint("mock:cacheGet").expectedHeaderReceived(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_GET);
        getMockEndpoint("mock:cacheGet").expectedHeaderValuesReceivedInAnyOrder(CacheConstants.CACHE_KEY, "OSL", "BGO", "TRD");

        List<ScheduledStopover> stopovers = enrichStopoversTemplate.requestBody(scheduledStopovers, List.class);

        assertMockEndpointsSatisfied();

        Assertions.assertThat(stopovers)
                .isNotNull()
                .isNotEmpty()
                .hasSize(3)
                .hasOnlyElementsOfType(ScheduledStopover.class)
                .allMatch((Predicate<ScheduledStopover>) stopover ->
                        stopover.getAirportName().equals("DUMMY-AIRPORT-NAME"));
    }

    @Test
    public void testAddAirportNameToCache() throws Exception {
        context.getRouteDefinition("AirportNameAddToCache").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("TimetableCacheAddProcessor").replace().to("mock:cacheAdd");
            }
        });
        context.start();

        getMockEndpoint("mock:cacheAdd").expectedMessageCount(1);
        getMockEndpoint("mock:cacheAdd").expectedHeaderReceived(HEADER_TIMETABLE_AIRPORT_IATA, "OSL");
        getMockEndpoint("mock:cacheAdd").expectedHeaderReceived(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_ADD);
        getMockEndpoint("mock:cacheAdd").expectedHeaderReceived(CacheConstants.CACHE_KEY, "OSL");
        getMockEndpoint("mock:cacheAdd").expectedBodiesReceived("Oslo");

        template.sendBodyAndHeader("direct:addAirportNameToCache", "Oslo", HEADER_TIMETABLE_AIRPORT_IATA, "OSL");

        assertMockEndpointsSatisfied();
    }

    private Range<LocalDate> createRange(String lower, String upper) {
        return Range.closed(LocalDate.parse(lower), LocalDate.parse(upper));
    }

    private List<Flight> createDummyFlights() {
        return Lists.newArrayList(
                createDummyFlight(1L, "SK", "4455", LocalDate.parse("2017-01-01"), "BGO", LocalTime.MIN, "OSL", LocalTime.MAX),
                createDummyFlight(2L, "DY", "6677", LocalDate.parse("2017-01-02"), "BGO", LocalTime.MIN, "TRD", LocalTime.MAX),
                createDummyFlight(3L, "WF", "199", LocalDate.parse("2017-01-03"), "BGO", LocalTime.MIN, "SVG", LocalTime.MAX)
        );
    }

    private Flight createDummyFlight(long dummyId, String dummyDesignator, String dummyFlightNumber, LocalDate dummyDateOfOperation,
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

    private ScheduledStopoverFlight createDummyScheduledStopoverFlight(long dummyId, String dummyAirlineIata,
                                                                       String dummyFlightId, LocalDate dummyDateOfOperation,
                                                                       List<ScheduledStopover> stopovers) {
        return new ScheduledStopoverFlight() {{
            setId(BigInteger.valueOf(dummyId));
            setAirlineIATA(dummyAirlineIata);
            setAirlineFlightId(dummyFlightId);
            setDateOfOperation(dummyDateOfOperation);
            getScheduledStopovers().addAll(stopovers);
        }};
    }

    private ScheduledStopover createDummyScheduledStopover(long dummyId, String dummyAirportIata) {
        return new ScheduledStopover() {{
            setId(BigInteger.valueOf(dummyId));
            setAirportIATA(dummyAirportIata);
        }};
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