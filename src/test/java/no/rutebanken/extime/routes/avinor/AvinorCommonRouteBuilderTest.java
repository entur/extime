package no.rutebanken.extime.routes.avinor;

import com.google.common.collect.Maps;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import java.util.Map;

import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_EXTIME_HTTP_URI;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_EXTIME_URI_PARAMETERS;

public class AvinorCommonRouteBuilderTest extends CamelTestSupport {

    @Test
    public void testFetchFromHttpResource() throws Exception {
        context.getRouteDefinition("FetchXmlFromHttpFeed").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("FetchXmlFromHttpFeedProcessor").replace().to("mock:fetchFromHttpEndpoint");
            }
        });
        context.start();

        getMockEndpoint("mock:fetchFromHttpEndpoint").expectedMessageCount(1);
        getMockEndpoint("mock:fetchFromHttpEndpoint").expectedHeaderReceived(Exchange.HTTP_METHOD, HttpMethods.GET);
        getMockEndpoint("mock:fetchFromHttpEndpoint").expectedHeaderReceived(
                Exchange.HTTP_QUERY, "airport=TRD&direction=D&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z");
        getMockEndpoint("mock:fetchFromHttpEndpoint").expectedHeaderReceived(
                HEADER_EXTIME_HTTP_URI, "http4://195.69.13.136/XmlFeedScheduled.asp");

        Map<String,Object> headers = Maps.newHashMap();
        headers.put(HEADER_EXTIME_HTTP_URI, "http://195.69.13.136/XmlFeedScheduled.asp");
        headers.put(HEADER_EXTIME_URI_PARAMETERS, "airport=TRD&direction=D&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z");

        template.sendBodyAndHeaders("direct:fetchXmlStreamFromHttpFeed", null, headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new AvinorCommonRouteBuilder();
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
    protected void debugBefore(Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String shortName) {
        log.info("Before " + definition + " with body " + exchange.getIn().getBody());
    }

    @Override
    protected void debugAfter(Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String label, long timeTaken) {
        log.info("After " + definition + " with body " + exchange.getIn().getBody());
    }

}