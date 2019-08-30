package no.rutebanken.extime.routes.avinor;

import no.rutebanken.extime.util.AvinorTimetableUtils;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cache.CacheConstants;
import org.apache.camel.component.http4.HttpMethods;
import org.springframework.stereotype.Component;

import javax.xml.transform.stream.StreamSource;

@Component
public class AvinorCommonRouteBuilder extends RouteBuilder {

    public static final String DEFAULT_HTTP_CHARSET = "iso-8859-1";

    public static final String HEADER_EXTIME_RESOURCE_CODE = "ExtimeResourceCode";
    public static final String HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT = "ExtimeFetchResourceEndpoint";
    public static final String HEADER_EXTIME_HTTP_URI = "ExtimeHttpUri";
    public static final String HEADER_EXTIME_URI_PARAMETERS = "ExtimeUriParameters";

    @Override
    public void configure() throws Exception {

        from("cache://avinorResourceCache" +
                "?maxElementsInMemory=1000" +
                "&overflowToDisk=true" +
                "&eternal=true" +
                "&timeToLiveSeconds=300" +
                "&overflowToDisk=true" +
                "&diskPersistent=true" +
                "&diskExpiryThreadIntervalSeconds=300")
                .routeId("AvinorResourceCache")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "${header.CamelCacheKey}:${body}")
        ;

        from("direct:addResourceToCache")
                .routeId("AddResourceToCache")
                .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD))
                .setHeader(CacheConstants.CACHE_KEY, simpleF("${header.%s}", HEADER_EXTIME_RESOURCE_CODE))
                .log(LoggingLevel.DEBUG, this.getClass().getName(), String.format("Adding resource to cache: ${header.%s}:${body}", CacheConstants.CACHE_KEY))
                .to("cache://avinorResourceCache").id("CacheAddResourceProcessor")
        ;

        from("direct:getResourceFromCache")
                .routeId("GetResourceFromCache")
                .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_GET))
                .setHeader(CacheConstants.CACHE_KEY, body())
                .to("cache://avinorResourceCache").id("CacheGetResourceProcessor")
        ;

        from("direct:retrieveResource")
                .routeId("ResourceRetriever")
                .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_CHECK))
                .setHeader(CacheConstants.CACHE_KEY, body())
                .to("cache://avinorResourceCache").id("ResourceCacheCheckProcessor")

                .choice()
                    .when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNull())
                        .setHeader(HEADER_EXTIME_RESOURCE_CODE, body())
                        .toD("${header.ExtimeFetchResourceEndpoint}").id("DynamicFetchResourceProcessor")
                        .to("direct:getResourceFromCache")
                    .otherwise()
                        .to("direct:getResourceFromCache")
                .end()
        ;

        from("direct:fetchXmlStreamFromHttpFeed")
                .routeId("FetchXmlFromHttpFeed")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .setHeader(HEADER_EXTIME_HTTP_URI).method(AvinorTimetableUtils.class, "useHttp4Client")
                .setHeader(Exchange.HTTP_QUERY, simpleF("${header.%s}", HEADER_EXTIME_URI_PARAMETERS))
                .log(LoggingLevel.DEBUG, this.getClass().getName(), String.format("HTTP URI HEADER: ${header.%s}", HEADER_EXTIME_HTTP_URI))
                .log(LoggingLevel.DEBUG, this.getClass().getName(), String.format("HTTP QUERY HEADER: ${header.%s}", Exchange.HTTP_QUERY))
                .setBody(constant(null))
                .throttle(1).timePeriodMillis(1000)
                    .toD("${header.ExtimeHttpUri}").id("FetchXmlFromHttpFeedProcessor")
                .end()
                .convertBodyTo(StreamSource.class, DEFAULT_HTTP_CHARSET)
        ;

    }
}
