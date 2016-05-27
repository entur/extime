package no.rutebanken.extime.routes.avinor;

import no.avinor.flydata.xjc.model.scheduled.Flight;

import java.util.List;
import java.util.Map;

public class ScheduledFlightRouteAnalyzer {

    public void analyzeFlightRoutes(Map<String, List<Flight>> scheduledFlightsByAirport) {
/*
        scheduledFlightsByAirport.forEach(
                (iata, scheduledFlights) -> {
                    System.out.println(iata);
                    scheduledFlights.forEach(
                            flight -> System.out.printf("   %s %s %s-%s%n",
                                    flight.getDateOfOperation(),
                                    flight.getStd(),
                                    flight.getDepartureStation(),
                                    flight.getArrivalStation()));
                }
        );
*/
    }
}
