package no.rutebanken.extime.routes.avinor;

import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.model.ScheduledDirectFlight;
import no.rutebanken.extime.model.ScheduledStopover;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ScheduledFlightRouteAnalyzer {

    public void analyzeFlightRoutes(List<Flight> scheduledFlights) {
        Map<String, List<Flight>> flightsByDepartureAirport = scheduledFlights.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));

        List<ScheduledDirectFlight> scheduledDirectFlights = new ArrayList<>();

        scheduledFlights.forEach(scheduledFlight -> {
            ScheduledDirectFlight scheduledDirectFlight = convertToScheduledDirectFlight(scheduledFlight);
            scheduledDirectFlights.add(scheduledDirectFlight);

            List<Flight> arrivalDepartureList = flightsByDepartureAirport.get(scheduledFlight.getArrivalStation());
            Predicate<Flight> stopoverFlightPredicate = createStopoverFlightPredicate(scheduledFlight);

            Optional<Flight> optionalStopoverFlight = arrivalDepartureList.stream()
                    .filter(stopoverFlightPredicate)
                    .findFirst();

            if (optionalStopoverFlight.isPresent()) {
                // do something...
            }
        });
    }

    private Predicate<Flight> createStopoverFlightPredicate(Flight previousFlight) {
        Predicate<Flight> uniqueIdPredicate = nextFlight -> nextFlight.getId().subtract(previousFlight.getId()).equals(BigInteger.ONE);
        Predicate<Flight> designatorPredicate = nextFlight -> nextFlight.getAirlineDesignator().equalsIgnoreCase(previousFlight.getAirlineDesignator());
        Predicate<Flight> flightNumberPredicate = nextFlight -> nextFlight.getFlightNumber().equalsIgnoreCase(previousFlight.getFlightNumber());
        //Predicate<Flight> dateOfOperationPredicate = nextFlight -> nextFlight.getDateOfOperation() == previousFlight.getDateOfOperation();
        Predicate<Flight> departureStationPredicate = nextFlight -> nextFlight.getDepartureStation().equalsIgnoreCase(previousFlight.getArrivalStation());
        return uniqueIdPredicate
                .and(designatorPredicate)
                .and(flightNumberPredicate);
                //.and(dateOfOperationPredicate);
    }

    private void findStopoverPath(Flight flight, List<Flight> flights) {

    }

    private ScheduledDirectFlight convertToScheduledDirectFlight(Flight scheduledFlight) {
        return null;
    }

    private ScheduledStopover findNextScheduledStopover() {
        boolean isFinalDestinationFound = false;
        return null;
    }
}
