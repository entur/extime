package no.rutebanken.extime.model;

import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FlightEventMapper {

    private final Set<String> whitelistedAirports;
    private final Set<String> whitelistedAirlines;

    public FlightEventMapper() {
        whitelistedAirports = Arrays.stream(AirportIATA.values())
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
        whitelistedAirlines = Arrays.stream(AirlineIATA.values())
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    public List<FlightEvent> mapToFlightEvent(Flights flightsInAirport) {
        List<Flight> flights = flightsInAirport.getFlight();
        if (flights == null) {
            return List.of();
        }
        return flights.stream()
                .filter(flight -> whitelistedAirports.contains(flight.getDepartureStation()))
                .filter(flight -> whitelistedAirports.contains(flight.getArrivalStation()))
                .filter(flight -> whitelistedAirlines.contains(flight.getAirlineDesignator()))
                // keeping only scheduled passenger flights (not charter flights)
                .filter(flight -> "J".equals(flight.getServiceType()) )
                // filtering out invalid input data with departure airport == arrival airport
                .filter(flight -> !flight.getDepartureStation().equals(flight.getArrivalStation()))
                .map(FlightEventMapper::toFlightEvent)
                .toList();
    }

    private static FlightEvent toFlightEvent(Flight flight) {
        AirlineIATA airline = AirlineIATA.valueOf(flight.getAirlineDesignator());
        String flightNumber = (airline.name() + flight.getFlightNumber()).intern();
        return new FlightEvent(
                flight.getId().longValue(),
                flightNumber,
                airline,
                        AirportIATA.valueOf(flight.getDepartureStation()),
                        AirportIATA.valueOf(flight.getArrivalStation()),
                        flight.getDateOfOperation(),
                        flight.getStd(),
                        flight.getSta()
                );
    }
}
