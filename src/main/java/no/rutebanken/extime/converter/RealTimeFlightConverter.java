package no.rutebanken.extime.converter;

import no.avinor.flydata.xjc.model.airport.AirportName;
import no.avinor.flydata.xjc.model.feed.Flight;
import no.rutebanken.extime.model.AirportFlightDataSet;
import no.rutebanken.extime.model.FlightRouteDataSet;
import org.apache.camel.Body;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component(value = "realTimeFlightConverter")
public class RealTimeFlightConverter {

    public List<FlightRouteDataSet> convertToRealTimeFlights(@Body Map<String, AirportFlightDataSet> airportFlightsMap) {
        Map<String, AirportName> airportNameCache = airportFlightsMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getAirportName()));

        Map<String, List<Flight>> departureFlightsMap = airportFlightsMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getDepartureFlights()));

        Map<String, List<Flight>> arrivalFlightsMap = airportFlightsMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getArrivalFlights()));

        List<FlightRouteDataSet> flightRouteDataSetList = new ArrayList<>();

        departureFlightsMap.forEach((airportIATA, departureFlights) -> {

            List<Flight> directFlights = departureFlights.stream()
                    .filter(flight -> flight.getViaAirport() == null || flight.getViaAirport().isEmpty())
                    .toList();

            directFlights.forEach(departureFlight -> {
                List<Flight> arrivalFlights = arrivalFlightsMap.get(departureFlight.getAirport().trim());
                Predicate<Flight> matchingFlightPredicate = createMatchingFlightPredicate(airportIATA, departureFlight);

                Optional<Flight> optionalArrivalFlight = arrivalFlights.stream()
                        .filter(matchingFlightPredicate)
                        .findFirst();

                if (optionalArrivalFlight.isPresent()) {
                    Flight matchingArrivalFlight = optionalArrivalFlight.get();
                    FlightRouteDataSet flightRouteDataSet = new FlightRouteDataSet();
                    flightRouteDataSet.setAirlineIATA(departureFlight.getAirline());
                    flightRouteDataSet.setFlightId(departureFlight.getFlightId());
                    flightRouteDataSet.setDepartureAirportName(airportNameCache.get(airportIATA));
                    flightRouteDataSet.setArrivalAirportName(airportNameCache.get(departureFlight.getAirport()));
                    flightRouteDataSet.setDepartureFlight(departureFlight);
                    flightRouteDataSet.setArrivalFlight(matchingArrivalFlight);
                    flightRouteDataSetList.add(flightRouteDataSet);
                }
            });
        });
        return flightRouteDataSetList;
    }

    private Predicate<Flight> createMatchingFlightPredicate(String airportIATA, Flight departureFlight) {
        Predicate<Flight> uniqueIdPredicate = arrivalFlight ->
                arrivalFlight.getUniqueID().subtract(departureFlight.getUniqueID()).equals(BigInteger.ONE);
        Predicate<Flight> airlineIATAPredicate = arrivalFlight ->
                arrivalFlight.getAirline().equalsIgnoreCase(departureFlight.getAirline());
        Predicate<Flight> flightIdPredicate = arrivalFlight ->
                arrivalFlight.getFlightId().equalsIgnoreCase(departureFlight.getFlightId());
        Predicate<Flight> airportIATAPredicate = arrivalFlight ->
                arrivalFlight.getAirport().equalsIgnoreCase(airportIATA);
        return uniqueIdPredicate
                .and(airlineIATAPredicate)
                .and(flightIdPredicate)
                .and(airportIATAPredicate);
    }

}
