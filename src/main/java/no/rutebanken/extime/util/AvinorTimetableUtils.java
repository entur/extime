package no.rutebanken.extime.util;

import no.avinor.flydata.xjc.model.scheduled.Flight;
import org.apache.camel.Header;

import java.util.List;

import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_EXTIME_HTTP_URI;

public class AvinorTimetableUtils {

    public String useHttp4Client(@Header(HEADER_EXTIME_HTTP_URI) String httpUri) {
        return httpUri.replace("http", "http4");
    }

    public void findUniqueAirlines(List<Flight> flights) {
        System.out.printf("%nUnique airline designators:%n");
        flights.stream()
                .map(Flight::getAirlineDesignator)
                .distinct()
                .sorted()
                .forEach(System.out::println);
    }

}
