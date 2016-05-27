package no.rutebanken.extime.routes.avinor;

import no.avinor.flydata.xjc.model.airport.AirportNames;
import no.avinor.flydata.xjc.model.feed.Flight;
import no.rutebanken.extime.model.FlightDirection;
import no.rutebanken.extime.model.FlightType;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.properties.DefaultPropertiesParser;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.*;

public class AvinorTimetableRouteBuilderTest extends CamelTestSupport {

    @Test
    @Ignore
    public void testAirportFlightsFeedRoute() throws Exception {
        context.getRouteDefinition("TimetableFetcher").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:flightFeedEndpoint")
                        .process(exchange -> {
                            InputStream inputStream = new FileInputStream("target/classes/xml/departures-osl.xml");
                            exchange.getIn().setBody(inputStream);
                        });
                weaveAddLast().to("mock:output");
            }
        });
        context.start();

        getMockEndpoint("mock:flightFeedEndpoint").expectedMessageCount(1);
        getMockEndpoint("mock:flightFeedEndpoint").expectedHeaderReceived(Exchange.HTTP_METHOD, HttpMethods.GET);
        getMockEndpoint("mock:flightFeedEndpoint").expectedHeaderReceived(
                Exchange.HTTP_QUERY, "airport=OSL&timeFrom=0&timeTo=72&direction=D");

        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:output").expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_TIMETABLE_AIRPORT_IATA, "OSL");
        headers.put(HEADER_FLIGHTS_TIMEFROM, "0");
        headers.put(HEADER_FLIGHTS_TIMETO, "72");
        headers.put(HEADER_FLIGHTS_DIRECTION, FlightDirection.DEPARTURE.getCode());

        template.sendBodyAndHeaders("direct:fetchAirportFlights", "OSL", headers);

        assertMockEndpointsSatisfied();

        MockEndpoint mockOutput = getMockEndpoint("mock:output");
        mockOutput.expectedMessageCount(1);
        Object body = mockOutput.getReceivedExchanges().get(0).getIn().getBody();

        Assertions.assertThat(body).asList()
                .isNotNull()
                .isInstanceOf(List.class);

        Assertions.assertThat((List<Flight>) body)
                .isNotEmpty()
                .hasSize(3)
                .doesNotHaveDuplicates()
                .allMatch((Predicate<Flight>) flight -> flight.getDomInt().equalsIgnoreCase(FlightType.DOMESTIC.getCode()));
    }

    @Test
    @Ignore
    public void testAirportNameEnricherRoute() throws Exception {
        context.getRouteDefinition("AirportNameEnricher").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:airportFeedEndpoint")
                        .process(exchange -> {
                            InputStream inputStream = new FileInputStream("target/classes/xml/airportname-osl.xml");
                            exchange.getIn().setBody(inputStream);
                        });
                weaveAddLast().to("mock:output");
            }
        });
        context.start();

        getMockEndpoint("mock:airportFeedEndpoint").expectedMessageCount(1);
        getMockEndpoint("mock:airportFeedEndpoint").expectedHeaderReceived(Exchange.HTTP_METHOD, HttpMethods.GET);
        getMockEndpoint("mock:airportFeedEndpoint").expectedHeaderReceived(Exchange.HTTP_QUERY, "airport=OSL&shortname=Y&ukname=Y");

        getMockEndpoint("mock:output").expectedMessageCount(1);
        getMockEndpoint("mock:output").expectedBodyReceived().body(AirportNames.class);

        template.sendBodyAndHeader("direct:fetchAirportNameResource", "OSL", HEADER_TIMETABLE_AIRPORT_IATA, "OSL");
        assertMockEndpointsSatisfied();
    }

    @Test
    @Ignore
    public void testAirportNameEnricherRouteOnException() throws Exception {
        context.getRouteDefinition("AirportNameEnricher").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveAddLast().to("mock:output");
            }
        });
        context.start();

        getMockEndpoint("mock:airportFeedEndpoint").whenAnyExchangeReceived(
                exchange -> exchange.setException(new ConnectException("SIMULATED: Connection timed out")));

        getMockEndpoint("mock:output").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:fetchAirportNameResource", "OSL", HEADER_TIMETABLE_AIRPORT_IATA, "OSL");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        PropertiesComponent pc = (PropertiesComponent) context.getComponent("properties");
        pc.setPropertiesParser(new DefaultPropertiesParser() {
            @Override
            public String parseProperty(String key, String value, Properties properties) {
                Map<String, String> testProperties = new HashMap<String, String>() {{
                    put("avinor.timetable.scheduler.cron", "direct:start");
                    put("avinor.timetable.departures.timefrom", "0");
                    put("avinor.timetable.departures.timeto", "72");
                    put("avinor.timetable.arrivals.timefrom", "0");
                    put("avinor.timetable.arrivals.timeto", "96");
                    put("avinor.timetable.feed.endpoint", "mock:flightFeedEndpoint");
                    put("avinor.airport.feed.endpoint", "mock:airportFeedEndpoint");
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