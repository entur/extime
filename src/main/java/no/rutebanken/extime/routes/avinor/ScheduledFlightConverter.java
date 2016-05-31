package no.rutebanken.extime.routes.avinor;

import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.model.*;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.math.BigInteger;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ScheduledFlightConverter {

    public ScheduledFlightDataSet convertToScheduledFlights(List<Flight> scheduledFlights) {
        Map<String, List<Flight>> flightsByDepartureAirport = scheduledFlights.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));

        ScheduledFlightDataSet scheduledFlightDataSet = new ScheduledFlightDataSet();
        List<ScheduledDirectFlight> scheduledDirectFlights = new ArrayList<>();
        List<ScheduledStopoverFlight> scheduledStopoverFlights = new ArrayList<>();

        // @todo: remove this mod after testing
        List<Flight> scheduledFlightsMod = scheduledFlights.stream()
                .filter(flight ->
                        flight.getArrivalStation().equalsIgnoreCase("OSL") ||
                        flight.getArrivalStation().equalsIgnoreCase("BGO") ||
                        flight.getArrivalStation().equalsIgnoreCase("HOV") ||
                        flight.getArrivalStation().equalsIgnoreCase("SOG"))
                .collect(Collectors.toList());

        scheduledFlightsMod.forEach(scheduledFlight -> {
            scheduledDirectFlights.add(convertToScheduledDirectFlight(scheduledFlight));

            List<ScheduledStopover> scheduledStopovers = findPossibleStopoversForFlight(scheduledFlight, flightsByDepartureAirport);
            if (!scheduledStopovers.isEmpty()) {
                ScheduledStopoverFlight scheduledStopoverFlight = new ScheduledStopoverFlight();
                scheduledStopoverFlight.setFlightId(String.format("%s%s",
                        scheduledFlight.getAirlineDesignator(), scheduledFlight.getFlightNumber()));
                scheduledStopoverFlight.setDateOfOperation(scheduledFlight.getDateOfOperation());
                scheduledStopoverFlight.getScheduledStopovers().addAll(scheduledStopovers);
                scheduledStopoverFlights.add(scheduledStopoverFlight);
            }
        });
        scheduledFlightDataSet.getScheduledDirectFlights().addAll(scheduledDirectFlights);
        scheduledFlightDataSet.getScheduledStopoverFlights().addAll(scheduledStopoverFlights);
        return scheduledFlightDataSet;
    }

    public List<ScheduledStopover> findPossibleStopoversForFlight(Flight currentFlight, Map<String, List<Flight>> flightsByDepartureAirport) {
        List<ScheduledStopover> scheduledStopovers = new ArrayList<>();
        List<Flight> foundStopoverFlights = findPossibleStopoversForFlight(currentFlight, flightsByDepartureAirport, new ArrayList<>());
        if (!foundStopoverFlights.isEmpty()) {
            List<Triple<StopVisitType, String, LocalTime>> stopovers = extractStopoversFromFlights(foundStopoverFlights);
            Triple<StopVisitType, String, LocalTime> tempArrivalStopover = null;
            for (ListIterator<Triple<StopVisitType, String, LocalTime>> it = stopovers.listIterator(); it.hasNext(); ) {
                Triple<StopVisitType, String, LocalTime> stopover = it.next();
                if (stopover.getLeft().equals(StopVisitType.DEPARTURE)) {
                    ScheduledStopover scheduledStopover = new ScheduledStopover();
                    scheduledStopover.setAirportIATA(stopover.getMiddle());
                    scheduledStopover.setDepartureTime(stopover.getRight());
                    if (tempArrivalStopover != null) {
                        scheduledStopover.setArrivalTime(tempArrivalStopover.getRight());
                    }
                    scheduledStopovers.add(scheduledStopover);
                } else {
                    if (!it.hasNext()) {
                        ScheduledStopover scheduledStopover = new ScheduledStopover();
                        scheduledStopover.setAirportIATA(stopover.getMiddle());
                        scheduledStopover.setArrivalTime(stopover.getRight());
                        scheduledStopovers.add(scheduledStopover);
                    } else {
                        tempArrivalStopover = stopover;
                    }
                }
            }
        }
        if (scheduledStopovers.isEmpty()) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(scheduledStopovers);
        }
    }

    public List<Flight> findPossibleStopoversForFlight(Flight currentFlight,
                                                       Map<String, List<Flight>> flightsByDepartureAirport, List<Flight> stopoverFlights) {
        List<Flight> destinationFlights = flightsByDepartureAirport.get(currentFlight.getArrivalStation());
        Flight foundStopoverFlight = findPresentStopoverFlight(currentFlight, destinationFlights);
        if (foundStopoverFlight != null) {
            stopoverFlights.add(foundStopoverFlight);
            findPossibleStopoversForFlight(foundStopoverFlight, flightsByDepartureAirport, stopoverFlights);
        }
        if (stopoverFlights.isEmpty()) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(stopoverFlights);
        }
    }

    public Flight findPresentStopoverFlight(Flight currentFlight, List<Flight> destinationFlights) {
        if (destinationFlights == null) {
            System.out.println("DESTINATION FLIGHTS IS NULL!!!");
            System.out.println("CURRENT FLIGHT INFO: " + currentFlight);
        }
        Optional<Flight> optionalStopoverFlight = destinationFlights.stream()
                .filter(createStopoverFlightPredicate(currentFlight))
                .findFirst();
        if (optionalStopoverFlight.isPresent()) {
            return optionalStopoverFlight.get();
        }
        return null;
    }

    public Predicate<Flight> createStopoverFlightPredicate(Flight previousFlight) {
        Predicate<Flight> uniqueIdPredicate = nextFlight ->
                nextFlight.getId().subtract(previousFlight.getId()).equals(BigInteger.ONE);

        Predicate<Flight> designatorPredicate = nextFlight ->
                nextFlight.getAirlineDesignator().equalsIgnoreCase(previousFlight.getAirlineDesignator());

        Predicate<Flight> flightNumberPredicate = nextFlight ->
                nextFlight.getFlightNumber().equalsIgnoreCase(previousFlight.getFlightNumber());

        Predicate<Flight> dateOfOperationPredicate = nextFlight ->
                nextFlight.getDateOfOperation().equals(previousFlight.getDateOfOperation()) ||
                        nextFlight.getDateOfOperation().equals(previousFlight.getDateOfOperation().plusDays(1L));

        Predicate<Flight> departureStationPredicate = nextFlight ->
                nextFlight.getDepartureStation().equalsIgnoreCase(previousFlight.getArrivalStation());

        Predicate<Flight> arrivalDepartureTimePredicate = nextFlight ->
                nextFlight.getStd().isAfter(previousFlight.getSta());

        return uniqueIdPredicate
                .and(designatorPredicate)
                .and(flightNumberPredicate)
                .and(dateOfOperationPredicate)
                .and(departureStationPredicate)
                .and(arrivalDepartureTimePredicate);
    }

    public List<Triple<StopVisitType, String, LocalTime>> extractStopoversFromFlights(List<Flight> stopoverFlights) {
        List<Triple<StopVisitType, String, LocalTime>> stopoverTriples = new ArrayList<>();
        stopoverFlights.forEach(stopoverFlight -> {
            Triple<StopVisitType, String, LocalTime> departureTriple = new ImmutableTriple<>(
                    StopVisitType.DEPARTURE, stopoverFlight.getDepartureStation(), stopoverFlight.getStd());

            Triple<StopVisitType, String, LocalTime> arrivalTriple = new ImmutableTriple<>(
                    StopVisitType.ARRIVAL, stopoverFlight.getArrivalStation(), stopoverFlight.getSta());

            stopoverTriples.add(departureTriple);
            stopoverTriples.add(arrivalTriple);
        });
        if (stopoverTriples.isEmpty()) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(stopoverTriples);
        }
    }

    public ScheduledDirectFlight convertToScheduledDirectFlight(Flight scheduledFlight) {
        ScheduledDirectFlight scheduledDirectFlight = new ScheduledDirectFlight();
        scheduledDirectFlight.setFlightId(scheduledFlight.getId());
        scheduledDirectFlight.setAirlineIATA(scheduledFlight.getAirlineDesignator());
        scheduledDirectFlight.setAirlineFlightId(String.format("%s%s", scheduledFlight.getAirlineDesignator(), scheduledFlight.getFlightNumber()));
        scheduledDirectFlight.setDateOfOperation(scheduledFlight.getDateOfOperation());
        scheduledDirectFlight.setDepartureAirportIATA(scheduledFlight.getDepartureStation());
        scheduledDirectFlight.setArrivalAirportIATA(scheduledFlight.getArrivalStation());
        scheduledDirectFlight.setTimeOfDeparture(scheduledFlight.getStd());
        scheduledDirectFlight.setTimeOfArrival(scheduledFlight.getSta());
        return scheduledDirectFlight;
    }

}
