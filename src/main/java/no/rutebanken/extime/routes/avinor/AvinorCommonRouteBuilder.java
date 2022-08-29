package no.rutebanken.extime.routes.avinor;

import no.rutebanken.extime.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.ehcache.EhcacheConstants;
import org.apache.camel.component.http.HttpMethods;
import org.springframework.stereotype.Component;

import javax.xml.transform.stream.StreamSource;

@Component
public class AvinorCommonRouteBuilder extends BaseRouteBuilder {


    public static final String HEADER_EXTIME_RESOURCE_CODE = "ExtimeResourceCode";
    public static final String HEADER_EXTIME_FETCH_RESOURCE_ENDPOINT = "ExtimeFetchResourceEndpoint";
    public static final String HEADER_EXTIME_HTTP_URI = "ExtimeHttpUri";
    public static final String HEADER_EXTIME_URI_PARAMETERS = "ExtimeUriParameters";

    @Override
    public void configure() {

        super.configure();

        from("ehcache://avinorResourceCache?configurationUri=ehcache-extime.xml")
                .routeId("AvinorResourceCache")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "${header.CamelCacheKey}:${body}")
        ;

        from("direct:addResourceToCache")
                .routeId("AddResourceToCache")
                .setHeader(EhcacheConstants.ACTION, constant(EhcacheConstants.ACTION_PUT))
                .setHeader(EhcacheConstants.KEY, simpleF("${header.%s}", HEADER_EXTIME_RESOURCE_CODE))
                .log(LoggingLevel.DEBUG, this.getClass().getName(), String.format("Adding resource to ehcache: ${header.%s}:${body}", EhcacheConstants.KEY))
                .to("ehcache://avinorResourceCache").id("CacheAddResourceProcessor")
        ;

        from("direct:getResourceFromCache")
                .routeId("GetResourceFromCache")
                .setHeader(EhcacheConstants.ACTION, constant(EhcacheConstants.ACTION_GET))
                .setHeader(EhcacheConstants.KEY, body())
                .to("ehcache://avinorResourceCache").id("CacheGetResourceProcessor")
        ;

        from("direct:retrieveResource")
                .routeId("ResourceRetriever")
                .setHeader(EhcacheConstants.ACTION, constant(EhcacheConstants.ACTION_GET))
                .setHeader(EhcacheConstants.KEY, body())
                .to("ehcache://avinorResourceCache").id("ResourceCacheCheckProcessor")
                .choice()
                    .when(header(EhcacheConstants.ACTION_HAS_RESULT).isEqualTo(false))
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
                .setHeader(Exchange.HTTP_QUERY, simpleF("${header.%s}", HEADER_EXTIME_URI_PARAMETERS))
                .log(LoggingLevel.DEBUG, this.getClass().getName(), String.format("HTTP URI HEADER: ${header.%s}", HEADER_EXTIME_HTTP_URI))
                .log(LoggingLevel.DEBUG, this.getClass().getName(), String.format("HTTP QUERY HEADER: ${header.%s}", Exchange.HTTP_QUERY))
                .setBody(constant(""))
                .throttle(1).timePeriodMillis(1000)
                .toD("${header." + HEADER_EXTIME_HTTP_URI + "}").id("FetchXmlFromHttpFeedProcessor")
                .end()
                // convert using the charset retrieved from the HTTP response header that Camel sets in the property Exchange.CHARSET_NAME
                .convertBodyTo(StreamSource.class)
                // remove the property Exchange.CHARSET_NAME after the conversion so that the JAXB formats can be overriden with a custom encoding
                .removeProperty(Exchange.CHARSET_NAME)
        ;

    }
}
