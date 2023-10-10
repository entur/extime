package no.rutebanken.extime.model;

import no.avinor.flydata.xjc.model.scheduled.Flight;

import java.util.function.Predicate;

public class FlightPredicate {

    private FlightPredicate() {
        // Should not be instantiated
    }

    public static Predicate<Flight> matchPreviousFlight(Flight currentFlight) {
        return matchPreviousFlightId(currentFlight)
                .and(matchDesignator(currentFlight))
                .and(matchFlightNumber(currentFlight))
                .and(matchDateOfOperation(currentFlight))
                .and(matchPreviousByIata(currentFlight))
                .and(matchPreviousByTime(currentFlight));
    }

    public static Predicate<Flight> matchNextFlight(Flight currentFlight) {
        return matchNextFlightId(currentFlight)
                .and(matchDesignator(currentFlight))
                .and(matchFlightNumber(currentFlight))
                .and(matchDateOfOperation(currentFlight))
                .and(matchNextByIata(currentFlight))
                .and(matchNextByTime(currentFlight));
    }

    public static Predicate<Flight> matchPreviousFlightId(Flight currentFlight) {
        return previousFlight ->
                currentFlight.getId().equals(previousFlight.getId()) ||
                        currentFlight.getId() - previousFlight.getId() == 1;
    }

    public static Predicate<Flight> matchNextFlightId(Flight currentFlight) {
        return nextFlight ->
                nextFlight.getId().equals(currentFlight.getId()) ||
                        nextFlight.getId() - currentFlight.getId() == 1;
    }

    public static Predicate<Flight> matchPreviousByIata(Flight currentFlight) {
        return previousFlight ->
                previousFlight.getArrivalStation().equalsIgnoreCase(currentFlight.getDepartureStation());
    }

    public static Predicate<Flight> matchNextByIata(Flight currentFlight) {
        return nextFlight ->
                nextFlight.getDepartureStation().equalsIgnoreCase(currentFlight.getArrivalStation());
    }

    public static Predicate<Flight> matchPreviousByTime(Flight currentFlight) {
        return previousFlight ->
                previousFlight.getSta().isBefore(currentFlight.getStd());
    }

    public static Predicate<Flight> matchNextByTime(Flight currentFlight) {
        return nextFlight ->
                nextFlight.getStd().isAfter(currentFlight.getSta());
    }

    public static Predicate<Flight> matchDesignator(Flight currentFlight) {
        return flight ->
                flight.getAirlineDesignator().equalsIgnoreCase(currentFlight.getAirlineDesignator());
    }

    public static Predicate<Flight> matchFlightNumber(Flight currentFlight) {
        return flight ->
                flight.getFlightNumber().equalsIgnoreCase(currentFlight.getFlightNumber());
    }

    public static Predicate<Flight> matchDateOfOperation(Flight currentFlight) {
        return flight ->
                flight.getDateOfOperation().equals(currentFlight.getDateOfOperation()) ||
                flight.getDateOfOperation().equals(currentFlight.getDateOfOperation().plusDays(1L));
    }

}
