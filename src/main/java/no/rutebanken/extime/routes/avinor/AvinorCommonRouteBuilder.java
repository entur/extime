package no.rutebanken.extime.routes.avinor;

import no.rutebanken.extime.util.AvinorTimetableUtils;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.springframework.stereotype.Component;

import javax.xml.transform.stream.StreamSource;

@Component
public class AvinorCommonRouteBuilder extends RouteBuilder {

    public static final String DEFAULT_HTTP_CHARSET = "iso-8859-1";

    public static final String HEADER_EXTIME_HTTP_URI = "ExtimeHttpUri";
    public static final String HEADER_EXTIME_URI_PARAMETERS = "ExtimeUriParameters";

    @Override
    public void configure() throws Exception {

        from("direct:fetchXmlStreamFromHttpFeed")
                .routeId("FetchXmlFromHttpFeed")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .setHeader(HEADER_EXTIME_HTTP_URI).method(AvinorTimetableUtils.class, "useHttp4Client")
                .setHeader(Exchange.HTTP_QUERY, simpleF("${header.%s}", HEADER_EXTIME_URI_PARAMETERS))
                .log(LoggingLevel.DEBUG, this.getClass().getName(), String.format("HTTP URI HEADER: ${header.%s}", HEADER_EXTIME_HTTP_URI))
                .log(LoggingLevel.DEBUG, this.getClass().getName(), String.format("HTTP QUERY HEADER: ${header.%s}", Exchange.HTTP_QUERY))
                .setBody(constant(null))
                .toD("${header.ExtimeHttpUri}").id("FetchXmlFromHttpFeedProcessor")
                .convertBodyTo(StreamSource.class, DEFAULT_HTTP_CHARSET)
        ;

    }
}
