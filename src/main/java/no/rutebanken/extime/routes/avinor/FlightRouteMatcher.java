package no.rutebanken.extime.routes.avinor;

import no.avinor.flydata.xjc.model.airport.AirportName;
import no.avinor.flydata.xjc.model.feed.Flight;
import no.rutebanken.extime.model.AirportFlightDataSet;
import no.rutebanken.extime.model.FlightRouteDataSet;
import org.apache.camel.Body;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component(value = "flightRouteMatcher")
public class FlightRouteMatcher {

    public List<FlightRouteDataSet> findMatchingFlightRoutes(@Body HashMap<String, AirportFlightDataSet> airportFlightsMap) {
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
                    .filter(flight ->
                            flight.getAirport().equalsIgnoreCase("OSL") ||
                            flight.getAirport().equalsIgnoreCase("BGO") ||
                            flight.getAirport().equalsIgnoreCase("TRD"))
                    .collect(Collectors.toList());

            directFlights.forEach(departureFlight -> {
                List<Flight> arrivalFlights = arrivalFlightsMap.get(departureFlight.getAirport().trim());
                Predicate<Flight> matchingFlightPredicate = createMatchingFlightPredicate(airportIATA, departureFlight);

                Optional<Flight> optionalArrivalFlight = arrivalFlights.stream()
                        .filter(matchingFlightPredicate)
                        .findFirst();

                if (optionalArrivalFlight.isPresent()) {
                    Flight matchingArrivalFlight = optionalArrivalFlight.get();
                    FlightRouteDataSet flightRouteDataSet = new FlightRouteDataSet();
                    flightRouteDataSet.setAirlineName(departureFlight.getAirline());
                    flightRouteDataSet.setFlightId(departureFlight.getFlightId());
                    flightRouteDataSet.setDepartureAirportName(airportNameCache.get(airportIATA));
                    flightRouteDataSet.setArrivalAirportName(airportNameCache.get(departureFlight.getAirport()));
                    flightRouteDataSet.setDepartureFlight(departureFlight);
                    flightRouteDataSet.setArrivalFlight(matchingArrivalFlight);
                    //flightRouteDataSet.setStopovers(null);
                    flightRouteDataSetList.add(flightRouteDataSet);
                }
            });
        });
        return flightRouteDataSetList;

/*
        Map<String, List<FlightRouteDataSet>> airlineRoutesMap =
                flightRouteDataSetList.stream()
                        .collect(Collectors.groupingBy(FlightRouteDataSet::getAirlineName));
        return airlineRoutesMap;
*/
    }

    private Predicate<Flight> createMatchingFlightPredicate(String airportIATA, Flight departureFlight) {
        Predicate<Flight> uniquieIdPredicate = arrivalFlight ->
                arrivalFlight.getUniqueID().subtract(departureFlight.getUniqueID()).equals(BigInteger.ONE);
        Predicate<Flight> airlineIATAPredicate = arrivalFlight ->
                arrivalFlight.getAirline().equalsIgnoreCase(departureFlight.getAirline());
        Predicate<Flight> flightIdPredicate = arrivalFlight ->
                arrivalFlight.getFlightId().equalsIgnoreCase(departureFlight.getFlightId());
        Predicate<Flight> airportIATAPredicate = arrivalFlight ->
                arrivalFlight.getAirport().equalsIgnoreCase(airportIATA);
        return uniquieIdPredicate
                .and(airlineIATAPredicate)
                .and(flightIdPredicate)
                .and(airportIATAPredicate);
    }

}
