package no.rutebanken.extime.routes.avinor;

import com.google.common.collect.Maps;
import no.rutebanken.extime.ExtimeRouteBuilderIntegrationTestBase;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.cache.CacheConstants;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {AvinorCommonRouteBuilder.class} , properties = {
        "spring.config.name=application,netex-static-data",
        "avinor.timetable.scheduler.consumer=direct:start"
})
public class AvinorCommonRouteBuilderTest extends ExtimeRouteBuilderIntegrationTestBase {


    @EndpointInject(uri = "mock:cacheAdd")
    protected MockEndpoint mockCacheAdd;

    @EndpointInject(uri = "mock:cacheGet")
    protected MockEndpoint mockCacheGet;

    @EndpointInject(uri = "mock:fetchFromHttpEndpoint")
    protected MockEndpoint mockFetchFromHttpEndpoint;

    @EndpointInject(uri = "mock:cacheCheck")
    protected MockEndpoint mockCacheCheck;

    @EndpointInject(uri = "mock:fetchResource")
    protected MockEndpoint mockFetchResource;

    @EndpointInject(uri = "mock:direct:getResourceFromCache")
    protected MockEndpoint mockGetResourceFromCache;

    @Produce(uri = "direct:addResourceToCache")
    protected ProducerTemplate addResourceToCacheTemplate;

    @Produce(uri = "direct:getResourceFromCache")
    protected ProducerTemplate getResourceFromCacheTemplate;


    @Produce(uri = "direct:retrieveResource")
    protected ProducerTemplate retrieveResourceTemplate;

    @Produce(uri = "direct:fetchXmlStreamFromHttpFeed")
    protected ProducerTemplate fetchXmlStreamFromHttpFeedTemplate;

    @Test
    public void testAddResourceToCache() throws Exception {
        context.getRouteDefinition("AddResourceToCache").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("CacheAddResourceProcessor").replace().to("mock:cacheAdd");
            }
        });
        context.start();

        mockCacheAdd.expectedMessageCount(1);
        mockCacheAdd.expectedHeaderReceived(HEADER_EXTIME_RESOURCE_CODE, "OSL");
        mockCacheAdd.expectedHeaderReceived(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_ADD);
        mockCacheAdd.expectedHeaderReceived(CacheConstants.CACHE_KEY, "OSL");
        mockCacheAdd.expectedBodiesReceived("Oslo");

        addResourceToCacheTemplate.sendBodyAndHeader("Oslo", HEADER_EXTIME_RESOURCE_CODE, "OSL");

        mockCacheAdd.assertIsSatisfied();
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

       mockCacheGet.expectedMessageCount(1);
       mockCacheGet.expectedHeaderReceived(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_GET);
       mockCacheGet.expectedHeaderReceived(CacheConstants.CACHE_KEY, "OSL");
       mockCacheGet.expectedBodiesReceived("OSL");

       getResourceFromCacheTemplate.sendBody("OSL");

       mockCacheGet.assertIsSatisfied();
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

        mockCacheCheck.expectedMessageCount(1);
        mockCacheCheck.expectedHeaderReceived(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_CHECK);
        mockCacheCheck.expectedHeaderReceived(CacheConstants.CACHE_KEY, "OSL");
        mockFetchResource.expectedMessageCount(0);
        mockGetResourceFromCache.expectedMessageCount(1);
        mockGetResourceFromCache.expectedBodiesReceived("OSL");

        retrieveResourceTemplate.sendBodyAndHeader("OSL", HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT, "direct:fetchAndCacheAirportName");

        mockCacheCheck.assertIsSatisfied();
        mockFetchResource.assertIsSatisfied();
        mockGetResourceFromCache.assertIsSatisfied();
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

        mockCacheCheck.expectedMessageCount(1);
        mockCacheCheck.expectedHeaderReceived(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_CHECK);
        mockCacheCheck.expectedHeaderReceived(CacheConstants.CACHE_KEY, "OSL");
        mockFetchResource.expectedMessageCount(1);
        mockFetchResource.expectedHeaderReceived(HEADER_EXTIME_RESOURCE_CODE, "OSL");
        mockFetchResource.expectedHeaderReceived(HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT, "direct:fetchAndCacheAirportName");
        mockGetResourceFromCache.expectedMessageCount(1);
        mockGetResourceFromCache.expectedBodiesReceived("OSL");

        retrieveResourceTemplate.sendBodyAndHeader("OSL", HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT, "direct:fetchAndCacheAirportName");

        mockCacheCheck.assertIsSatisfied();
        mockFetchResource.assertIsSatisfied();
        mockGetResourceFromCache.assertIsSatisfied();


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

        mockFetchFromHttpEndpoint.expectedMessageCount(1);
        mockFetchFromHttpEndpoint.expectedHeaderReceived(Exchange.HTTP_METHOD, HttpMethods.GET);
        mockFetchFromHttpEndpoint.expectedHeaderReceived(
                Exchange.HTTP_QUERY, "airport=TRD&direction=D&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z");
        mockFetchFromHttpEndpoint.expectedHeaderReceived(
                HEADER_EXTIME_HTTP_URI, "http4://195.69.13.136/XmlFeedScheduled.asp");

        Map<String,Object> headers = Maps.newHashMap();
        headers.put(HEADER_EXTIME_HTTP_URI, "http://195.69.13.136/XmlFeedScheduled.asp");
        headers.put(HEADER_EXTIME_URI_PARAMETERS, "airport=TRD&direction=D&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z");

        fetchXmlStreamFromHttpFeedTemplate.sendBodyAndHeaders(null, headers);

        mockFetchFromHttpEndpoint.assertIsSatisfied();
    }

}