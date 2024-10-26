package no.rutebanken.extime.model;

/**
 * A flight event is the arrival or the departure of a plane at a given instant in time.
 * @param eventType either departure or arrival.
 * @param flightId the unique id of a flight leg, shared by the departure flight event and arrival flight event.
 * @param flightNumber the flight number as communicated to passengers.
 * @param airline the airline IATA code.
 * @param airport the airport IATA code.
 * @param scheduledTime the scheduled time of the departure or arrival.
 */
public record FlightEvent(
        StopVisitType eventType,
        long flightId,
        String flightNumber,
        AirlineIATA airline,
        AirportIATA airport,
        java.time.ZonedDateTime scheduledTime) {
}
