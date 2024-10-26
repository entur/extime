package no.rutebanken.extime.model;

import no.avinor.flydata.xjc.model.scheduled.Airport;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FlightEventMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlightEventMapper.class);


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

    public List<FlightEvent> mapToFlightEvent(Airport airport) {
        return airport.getContent()
                .stream()
                .filter(Flights.class::isInstance)
                .map(Flights.class::cast)
                .map(Flights::getFlight)
                .flatMap(java.util.Collection::stream)
                .filter(flight -> whitelistedAirports.contains(flight.getAirport()))
                .filter(flight -> whitelistedAirlines.contains(flight.getAirline()))
                .map(flight -> toFlightEvent(airport, flight))
                .toList();
    }

    private static FlightEvent toFlightEvent(Airport airport, Flight flight) {
        return new FlightEvent(
                getEventType(flight.getArr_Dep()),
                flight.getUniqueID(),
                flight.getFlight_Id(),
                AirlineIATA.valueOf(flight.getAirline()),
                AirportIATA.valueOf(airport.getName()),
                flight.getSchedule_Time());
    }

    private static StopVisitType getEventType(String arrDep) {
        return switch (arrDep) {
            case "A" -> StopVisitType.ARRIVAL;
            case "D" -> StopVisitType.DEPARTURE;
            default -> throw new IllegalArgumentException("Unknown arrival/departure code: " + arrDep);
        };
    }

}
