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
    private static final Duration MAX_LAYOVER_TIME = Duration.ofHours(3);


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

    public boolean isPreviousLegOf(FlightLeg other) {
        return  hasSameAirlineAs(other)
                && hasSameFlightNumberAs(other)
                && other.departFromArrivalAirportOf(this)
                && other.departSoonAfterArrivalOf(this);
    }

    public boolean isNextLegOf(FlightLeg other) {
        return hasSameAirlineAs(other)
                && hasSameFlightNumberAs(other)
                && departFromArrivalAirportOf(other)
                && departSoonAfterArrivalOf(other);
    }

    boolean hasSameAirlineAs(FlightLeg other) {
        return this.getAirlineDesignator().equals(other.getAirlineDesignator());
    }

    boolean hasSameFlightNumberAs(FlightLeg other) {
        return this.getFlightNumber().equals(other.getFlightNumber());
    }

    boolean departFromArrivalAirportOf(FlightLeg other) {
        return this.getDepartureAirport().equals(other.getArrivalAirport());
    }

    boolean departSoonAfterArrivalOf(FlightLeg other) {
        Duration layover = Duration.between(other.getSta(), this.getStd());
        return layover.isPositive() && layover.compareTo(MAX_LAYOVER_TIME) < 0;
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
