package no.rutebanken.extime.model;

import java.time.Duration;
import java.time.ZonedDateTime;


/**
 * A flight leg describes the movement of a plane from a departure airport to an arrival airport at a given scheduled
 * departure time and a given scheduled arrival time.
 * A flight leg is identified by a unique id.
 */
public class FlightLeg {

    /**
     * Maximum layover time between 2 flight legs. If the time is shorter, the two flights legs are considered being part of the
     * same multi-leg flight.
     */
    private static final Duration MAX_LAYOVER_DURATION = Duration.ofHours(3);


    private final String departureAirport;
    private final String arrivalAirport;
    private final String airlineDesignator;
    private final String flightNumber;
    private final ZonedDateTime std;
    private final ZonedDateTime sta;
    private final long id;

    FlightLeg(String departureAirport, String arrivalAirport, String airlineDesignator, String flightNumber, ZonedDateTime std, ZonedDateTime sta, long id) {
        this.departureAirport = departureAirport;
        this.arrivalAirport = arrivalAirport;
        this.airlineDesignator = airlineDesignator;
        this.flightNumber = flightNumber;
        this.std = std;
        this.sta = sta;
        this.id = id;
    }

    public long getId() {
        return id;
    }
    public String getDepartureAirport() {
        return departureAirport;
    }
    public String getArrivalAirport() {
        return arrivalAirport;
    }
    public String getAirlineDesignator() {
        return airlineDesignator;
    }
    public ZonedDateTime getStd() {
        return std;
    }
    public ZonedDateTime getSta() {
        return sta;
    }
    public String getFlightNumber() {
        return flightNumber;
    }

    /**
     * Return true if this FlightLeg is the next leg of the given FlightLeg, in a multi-leg flight.
     * <br>This is true if this leg:
     * <ul>
     *     <li>has the same flight number as the given FlightLeg,</li>
     *     <li>departs from the airport where the given FlightLeg lands,</li>
     *     <li>departs soon after the given FlightLeg lands.</li>
     * </ul>
     * Flight numbers are unique across all airlines since they are prefixed by the airline IATA code.
     */
    public boolean isNextLegOf(FlightLeg other) {
        return hasSameFlightNumberAs(other)
                && departFromArrivalAirportOf(other)
                && departSoonAfterArrivalOf(other);
    }

    boolean hasSameFlightNumberAs(FlightLeg other) {
        return this.getFlightNumber().equals(other.getFlightNumber());
    }

    boolean departFromArrivalAirportOf(FlightLeg other) {
        return this.getDepartureAirport().equals(other.getArrivalAirport());
    }

    boolean departSoonAfterArrivalOf(FlightLeg other) {
        Duration layover = Duration.between(other.getSta(), this.getStd());
        return layover.isPositive() && layover.compareTo(MAX_LAYOVER_DURATION) < 0;
    }

    @Override
    public String toString() {
        return "FlightLeg{" +
                "flightNumber='" + flightNumber + '\'' +
                ", departureAirport='" + departureAirport + '\'' +
                ", arrivalAirport='" + arrivalAirport + '\'' +
                ", std=" + std +
                ", sta=" + sta +
                ", id=" + id +
                '}';
    }
}
