package no.rutebanken.extime.routes.avinor;

import com.google.common.collect.Maps;
import no.rutebanken.extime.ExtimeCamelRouteBuilderIntegrationTestBase;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.ehcache.EhcacheConstants;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_HTTP_URI;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_RESOURCE_CODE;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_URI_PARAMETERS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {AvinorCommonRouteBuilder.class} , properties = {
        "avinor.timetable.scheduler.consumer=direct:start"
})
class AvinorCommonRouteBuilderTest extends ExtimeCamelRouteBuilderIntegrationTestBase {


    @EndpointInject("mock:cacheAdd")
    protected MockEndpoint mockCacheAdd;

    @EndpointInject("mock:cacheGet")
    protected MockEndpoint mockCacheGet;

    @EndpointInject("mock:fetchFromHttpEndpoint")
    protected MockEndpoint mockFetchFromHttpEndpoint;

    @EndpointInject("mock:cacheCheck")
    protected MockEndpoint mockCacheCheck;

    @EndpointInject("mock:fetchResource")
    protected MockEndpoint mockFetchResource;

    @EndpointInject("mock:direct:getResourceFromCache")
    protected MockEndpoint mockGetResourceFromCache;

    @Produce("direct:addResourceToCache")
    protected ProducerTemplate addResourceToCacheTemplate;

    @Produce("direct:getResourceFromCache")
    protected ProducerTemplate getResourceFromCacheTemplate;


    @Produce("direct:retrieveResource")
    protected ProducerTemplate retrieveResourceTemplate;

    @Produce("direct:fetchXmlStreamFromHttpFeed")
    protected ProducerTemplate fetchXmlStreamFromHttpFeedTemplate;

    @Test
    void testAddResourceToCache() throws Exception {

        AdviceWith.adviceWith(context, "AddResourceToCache", a -> a.weaveById("CacheAddResourceProcessor").replace().to("mock:cacheAdd"));

        context.start();

        mockCacheAdd.expectedMessageCount(1);
        mockCacheAdd.expectedHeaderReceived(HEADER_EXTIME_RESOURCE_CODE, "OSL");
        mockCacheAdd.expectedHeaderReceived(EhcacheConstants.ACTION, EhcacheConstants.ACTION_PUT);
        mockCacheAdd.expectedHeaderReceived(EhcacheConstants.KEY, "OSL");
        mockCacheAdd.expectedBodiesReceived("Oslo");

        addResourceToCacheTemplate.sendBodyAndHeader("Oslo", HEADER_EXTIME_RESOURCE_CODE, "OSL");

        mockCacheAdd.assertIsSatisfied();
    }

    @Test
    void testGetResourceFromCache() throws Exception {

        AdviceWith.adviceWith(context, "GetResourceFromCache", a -> a.weaveById("CacheGetResourceProcessor").replace().to("mock:cacheGet"));

        context.start();

       mockCacheGet.expectedMessageCount(1);
       mockCacheGet.expectedHeaderReceived(EhcacheConstants.ACTION, EhcacheConstants.ACTION_GET);
       mockCacheGet.expectedHeaderReceived(EhcacheConstants.KEY, "OSL");
       mockCacheGet.expectedBodiesReceived("OSL");

       getResourceFromCacheTemplate.sendBody("OSL");

       mockCacheGet.assertIsSatisfied();
    }

    @Test
    void testRetrieveResourceWhenInCache() throws Exception {

        AdviceWith.adviceWith(context, "ResourceRetriever", a -> {
            a.weaveById("ResourceCacheCheckProcessor").replace().to("mock:cacheCheck");
            a.interceptSendToEndpoint("mock:cacheCheck").process(exchange -> exchange.getIn().setHeader(EhcacheConstants.ACTION_HAS_RESULT, true));
            a.weaveById("DynamicFetchResourceProcessor").replace().to("mock:fetchResource");
            a.mockEndpointsAndSkip("direct:getResourceFromCache");
        });

        context.start();

        mockCacheCheck.expectedMessageCount(1);
        mockCacheCheck.expectedHeaderReceived(EhcacheConstants.ACTION, EhcacheConstants.ACTION_GET);
        mockCacheCheck.expectedHeaderReceived(EhcacheConstants.KEY, "OSL");
        mockFetchResource.expectedMessageCount(0);
        mockGetResourceFromCache.expectedMessageCount(1);
        mockGetResourceFromCache.expectedBodiesReceived("OSL");

        retrieveResourceTemplate.sendBodyAndHeader("OSL", HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT, "direct:fetchAndCacheAirportName");

        mockCacheCheck.assertIsSatisfied();
        mockFetchResource.assertIsSatisfied();
        mockGetResourceFromCache.assertIsSatisfied();
    }

    @Test
    void testRetrieveResourceWhenNotInCache() throws Exception {

        AdviceWith.adviceWith(context, "ResourceRetriever", a -> {
            a.weaveById("ResourceCacheCheckProcessor").replace().to("mock:cacheCheck");
            a.interceptSendToEndpoint("mock:cacheCheck").process(exchange -> exchange.getIn().setHeader(EhcacheConstants.ACTION_HAS_RESULT, false));
            a.weaveById("DynamicFetchResourceProcessor").replace().to("mock:fetchResource");
            a.mockEndpointsAndSkip("direct:getResourceFromCache");
        });

        context.start();

        mockCacheCheck.expectedMessageCount(1);
        mockCacheCheck.expectedHeaderReceived(EhcacheConstants.ACTION, EhcacheConstants.ACTION_GET);
        mockCacheCheck.expectedHeaderReceived(EhcacheConstants.KEY, "OSL");
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
    void testFetchFromHttpResource() throws Exception {

        AdviceWith.adviceWith(context, "FetchXmlFromHttpFeed", a -> a. weaveById("FetchXmlFromHttpFeedProcessor").replace().to("mock:fetchFromHttpEndpoint"));

        context.start();

        mockFetchFromHttpEndpoint.expectedMessageCount(1);
        mockFetchFromHttpEndpoint.expectedHeaderReceived(Exchange.HTTP_METHOD, HttpMethods.GET);
        mockFetchFromHttpEndpoint.expectedHeaderReceived(
                Exchange.HTTP_QUERY, "airport=TRD&direction=D&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z");
        mockFetchFromHttpEndpoint.expectedHeaderReceived(
                HEADER_EXTIME_HTTP_URI, "https://flydata.avinor.no/XmlFeedScheduled.asp");

        Map<String,Object> headers = Maps.newHashMap();
        headers.put(HEADER_EXTIME_HTTP_URI, "https://flydata.avinor.no/XmlFeedScheduled.asp");
        headers.put(HEADER_EXTIME_URI_PARAMETERS, "airport=TRD&direction=D&PeriodFrom=2017-01-01Z&PeriodTo=2017-01-31Z");

        fetchXmlStreamFromHttpFeedTemplate.sendBodyAndHeaders(null, headers);

        mockFetchFromHttpEndpoint.assertIsSatisfied();
    }

}