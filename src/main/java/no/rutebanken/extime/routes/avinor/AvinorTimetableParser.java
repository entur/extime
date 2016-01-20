package no.rutebanken.extime.routes.avinor;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import no.avinor.flydata.traffic.Airport;
import no.avinor.flydata.traffic.Airport.Flights.Flight;

@Component(value = "avinorTimetableParser")
public class AvinorTimetableParser {

	public Object parse(List<Airport> airports) {

		for (Airport airport : airports) {
			List<Flight> flight = airport.getFlights().getFlight();
			List<Flight> domesticFlights = flight
					.stream()
					.filter(t -> t.getDomInt().equals("D"))
					.collect(Collectors.toList());
			flight.clear();
			flight.addAll(domesticFlights);
		}

		AvinorDataset dataset = new AvinorDataset();
		dataset.setAirports(airports);
		
		return dataset;

	}

}
