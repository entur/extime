package no.rutebanken.extime.routes.avinor;

import java.io.Serializable;
import java.util.List;

import no.avinor.flydata.airlines.AirlineNames;
import no.avinor.flydata.airports.AirportNames;
import no.avinor.flydata.traffic.Airport;

public class AvinorDataset implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -325255143988978232L;

	private List<Airport> airports;
	
	public List<Airport> getAirports() {
		return airports;
	}

	public void setAirports(List<Airport> airports) {
		this.airports = airports;
	}

	public AirportNames getAirportNames() {
		return airportNames;
	}

	public void setAirportNames(AirportNames airportNames) {
		this.airportNames = airportNames;
	}

	public AirlineNames getAirlineNames() {
		return airlineNames;
	}

	public void setAirlineNames(AirlineNames airlineNames) {
		this.airlineNames = airlineNames;
	}

	private AirportNames airportNames;
	
	private AirlineNames airlineNames;
	
}
