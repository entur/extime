package no.rutebanken.extime.model;

import java.time.ZonedDateTime;

public class FlightLegBuilder {
    private String departureAirport;
    private String arrivalAirport;
    private String airlineDesignator;
    private String flightNumber;
    private ZonedDateTime std;
    private ZonedDateTime sta;
    private long id;

    public FlightLegBuilder withDepartureAirport(String departureAirport) {
        this.departureAirport = departureAirport;
        return this;
    }

    public FlightLegBuilder withArrivalAirport(String arrivalAirport) {
        this.arrivalAirport = arrivalAirport;
        return this;
    }

    public FlightLegBuilder withAirlineDesignator(String airlineDesignator) {
        this.airlineDesignator = airlineDesignator;
        return this;
    }

    public FlightLegBuilder withFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
        return this;
    }

    public FlightLegBuilder withStd(ZonedDateTime std) {
        this.std = std;
        return this;
    }

    public FlightLegBuilder withSta(ZonedDateTime sta) {
        this.sta = sta;
        return this;
    }

    public FlightLegBuilder withId(long id) {
        this.id = id;
        return this;
    }

    public FlightLeg build() {
        return new FlightLeg(departureAirport, arrivalAirport, airlineDesignator, flightNumber, std, sta, id);
    }

}