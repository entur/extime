package no.rutebanken.extime.routes.avinor;

import com.google.common.collect.Maps;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cache.CacheConstants;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import java.util.Map;

import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.*;

public class AvinorCommonRouteBuilderTest extends CamelTestSupport {

    @Test
    public void testAddResourceToCache() throws Exception {
        context.getRouteDefinition("AddResourceToCache").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("CacheAddResourceProcessor").replace().to("mock:cacheAdd");
            }
        });
        context.start();

        getMockEndpoint("mock:cacheAdd").expectedMessageCount(1);
        getMockEndpoint("mock:cacheAdd").expectedHeaderReceived(HEADER_EXTIME_RESOURCE_CODE, "OSL");
        getMockEndpoint("mock:cacheAdd").expectedHeaderReceived(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_ADD);
        getMockEndpoint("mock:cacheAdd").expectedHeaderReceived(CacheConstants.CACHE_KEY, "OSL");
        getMockEndpoint("mock:cacheAdd").expectedBodiesReceived("Oslo");

        template.sendBodyAndHeader("direct:addResourceToCache", "Oslo", HEADER_EXTIME_RESOURCE_CODE, "OSL");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testGetResourceFromCache() throws Exception {
        context.getRouteDefinition("GetResourceFromCache").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("CacheGetResourceProcessor").replace().to("mock:cacheGet");
            }
        });
        context.start();

        getMockEndpoint("mock:cacheGet").expectedMessageCount(1);
        getMockEndpoint("mock:cacheGet").expectedHeaderReceived(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_GET);
        getMockEndpoint("mock:cacheGet").expectedHeaderReceived(CacheConstants.CACHE_KEY, "OSL");
        getMockEndpoint("mock:cacheGet").expectedBodiesReceived("OSL");

        template.sendBody("direct:getResourceFromCache", "OSL");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRetrieveResourceWhenInCache() throws Exception {
        context.getRouteDefinition("ResourceRetriever").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("ResourceCacheCheckProcessor").replace().to("mock:cacheCheck");
                interceptSendToEndpoint("mock:cacheCheck").process(exchange -> exchange.getIn().setHeader(CacheConstants.CACHE_ELEMENT_WAS_FOUND, 1));
                weaveById("DynamicFetchResourceProcessor").replace().to("mock:fetchResource");
                mockEndpointsAndSkip("direct:getResourceFromCache");
            }
        });
        context.start();

        getMockEndpoint("mock:cacheCheck").expectedMessageCount(1);
        getMockEndpoint("mock:cacheCheck").expectedHeaderReceived(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_CHECK);
        getMockEndpoint("mock:cacheCheck").expectedHeaderReceived(CacheConstants.CACHE_KEY, "OSL");
        getMockEndpoint("mock:fetchResource").expectedMessageCount(0);
        getMockEndpoint("mock:direct:getResourceFromCache").expectedMessageCount(1);
        getMockEndpoint("mock:direct:getResourceFromCache").expectedBodiesReceived("OSL");

        template.sendBodyAndHeader("direct:retrieveResource", "OSL", HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT, "direct:fetchAndCacheAirportName");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRetrieveResourceWhenNotInCache() throws Exception {
        context.getRouteDefinition("ResourceRetriever").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("ResourceCacheCheckProcessor").replace().to("mock:cacheCheck");
                interceptSendToEndpoint("mock:cacheCheck").process(exchange -> exchange.getIn().setHeader(CacheConstants.CACHE_ELEMENT_WAS_FOUND, null));
                weaveById("DynamicFetchResourceProcessor").replace().to("mock:fetchResource");
                mockEndpointsAndSkip("direct:getResourceFromCache");
            }
        });
        context.start();

        getMockEndpoint("mock:cacheCheck").expectedMessageCount(1);
        getMockEndpoint("mock:cacheCheck").expectedHeaderReceived(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_CHECK);
        getMockEndpoint("mock:cacheCheck").expectedHeaderReceived(CacheConstants.CACHE_KEY, "OSL");
        getMockEndpoint("mock:fetchResource").expectedMessageCount(1);
        getMockEndpoint("mock:fetchResource").expectedHeaderReceived(HEADER_EXTIME_RESOURCE_CODE, "OSL");
        getMockEndpoint("mock:fetchResource").expectedHeaderReceived(HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT, "direct:fetchAndCacheAirportName");
        getMockEndpoint("mock:direct:getResourceFromCache").expectedMessageCount(1);
        getMockEndpoint("mock:direct:getResourceFromCache").expectedBodiesReceived("OSL");

        template.sendBodyAndHeader("direct:retrieveResource", "OSL", HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT, "direct:fetchAndCacheAirportName");

        assertMockEndpointsSatisfied();
    }

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