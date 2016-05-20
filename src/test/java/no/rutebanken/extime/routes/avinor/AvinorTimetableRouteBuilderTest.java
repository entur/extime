package no.rutebanken.extime.routes.avinor;

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
import org.junit.Test;

import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_AIRPORT_IATA;

public class AvinorTimetableRouteBuilderTest extends CamelTestSupport {

    @Test
    public void testAirportNameEnricherRoute() throws Exception {
        context.getRouteDefinition("AirportNameEnricher").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveAddLast().to("mock:output");
            }
        });
        context.start();

        getMockEndpoint("mock:airportFeedEndpoint").expectedMessageCount(1);
        getMockEndpoint("mock:airportFeedEndpoint").expectedHeaderReceived(Exchange.HTTP_METHOD, HttpMethods.GET);
        getMockEndpoint("mock:airportFeedEndpoint").expectedHeaderReceived(Exchange.HTTP_QUERY, "airport=OSL&shortname=Y&ukname=Y");
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:output").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:fetchAirportNameResource", "OSL", HEADER_AIRPORT_IATA, "OSL");
        assertMockEndpointsSatisfied();
    }

    @Test
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

        MockEndpoint mockError = getMockEndpoint("mock:error");
        mockError.expectedMessageCount(1);
        mockError.message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isNotNull();

        getMockEndpoint("mock:output").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:fetchAirportNameResource", "OSL", HEADER_AIRPORT_IATA, "OSL");
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