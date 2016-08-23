package no.rutebanken.extime.util;

import com.google.common.collect.Lists;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.ScheduledFlight;
import no.rutebanken.extime.model.StopVisitType;
import org.apache.camel.Header;
import org.apache.commons.lang3.EnumUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;
import java.util.ArrayList;
import java.util.Arrays;
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

    public List<Flight> generateStaticFlights() throws Exception {
        AirportIATA[] airportIATAs = Arrays.stream(AirportIATA.values())
                .filter(iata -> !iata.equals(AirportIATA.OSL))
                .toArray(AirportIATA[]::new);
        ArrayList<Flight> generatedFlights = Lists.newArrayList();
        for (int i = 1; i <= 2; i++) {
            for (AirportIATA airportIATA : airportIATAs) {
                String resourceName = String.format("%s-%d.xml", airportIATA, i);
                Flights flightStructure = generateObjectsFromXml(String.format("/xml/testdata/%s", resourceName), Flights.class);
                List<Flight> flights = flightStructure.getFlight();
                generatedFlights.addAll(flights);
            }
        }
        ArrayList<Flight> filteredFlights = Lists.newArrayList();
        for (Flight flight : generatedFlights) {
            for (StopVisitType stopVisitType : StopVisitType.values()) {
                if (isValidFlight(stopVisitType, flight)) {
                    filteredFlights.add(flight);
                }
            }
        }
        return filteredFlights;
    }

    private boolean isValidFlight(StopVisitType stopVisitType, Flight newFlight) {
        switch (stopVisitType) {
            case ARRIVAL:
                return AirportIATA.OSL.name().equalsIgnoreCase(newFlight.getDepartureStation());
            case DEPARTURE:
                return isDomesticFlight(newFlight);
        }
        return false;
    }

    private boolean isDomesticFlight(Flight flight) {
        return isValidDepartureAndArrival(flight.getDepartureStation(), flight.getArrivalStation());
    }

    private boolean isValidDepartureAndArrival(String departureIATA, String arrivalIATA) {
        return EnumUtils.isValidEnum(AirportIATA.class, departureIATA)
                && EnumUtils.isValidEnum(AirportIATA.class, arrivalIATA);
    }

    private <T> T generateObjectsFromXml(String resourceName, Class<T> clazz) throws JAXBException {
        return JAXBContext.newInstance(clazz).createUnmarshaller().unmarshal(
                new StreamSource(getClass().getResourceAsStream(resourceName)), clazz).getValue();
    }


}
