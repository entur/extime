package no.rutebanken.extime.model;

import java.time.LocalTime;

/**
 * A flight event is a single flight between two airports reported by the Avinor API.
 * This is a value object that provides a simplified view of the JAXB-generated Flight object.
 * @param flightId the unique id of a flight leg.
 * @param flightNumber the flight number as communicated to passengers.
 * @param airline the airline IATA code.
 * @param departureAirport the departure airport IATA code.
 * @param arrivalAirport the arrival airport IATA code.
 * @param dateOfOperation the scheduled date of operation.
 * @param departureTime the departure time in the time zone of the date of operation.
 * @param arrivalTime the arrival time in the time zone of the date of operation.
 */
public record FlightEvent(
        long flightId,
        String flightNumber,
        AirlineIATA airline,
        AirportIATA departureAirport,
        AirportIATA arrivalAirport,
        java.time.ZonedDateTime dateOfOperation,
        LocalTime departureTime,
        LocalTime arrivalTime) {
}
