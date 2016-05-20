package no.rutebanken.extime.model;

import no.avinor.flydata.xjc.model.airport.AirportName;
import no.avinor.flydata.xjc.model.feed.Flight;

import java.util.ArrayList;
import java.util.List;

public class AirportFlightDataSet {

    private AirportName airportName;

    private List<Flight> departureFlights;
    private List<Flight> arrivalFlights;

    public AirportName getAirportName() {
        return airportName;
    }

    public void setAirportName(AirportName airportName) {
        this.airportName = airportName;
    }

    public List<Flight> getDepartureFlights() {
        if (departureFlights == null) {
            departureFlights = new ArrayList<>();
        }
        return departureFlights;
    }

    public List<Flight> getArrivalFlights() {
        if (arrivalFlights == null) {
            arrivalFlights = new ArrayList<>();
        }
        return arrivalFlights;
    }

}
