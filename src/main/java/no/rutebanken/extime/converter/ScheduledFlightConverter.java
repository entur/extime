package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.model.ScheduledDirectFlight;
import no.rutebanken.extime.model.ScheduledStopover;
import no.rutebanken.extime.model.ScheduledStopoverFlight;
import no.rutebanken.extime.model.StopVisitType;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.math.BigInteger;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ScheduledFlightConverter {

    public List<ScheduledDirectFlight> convertToScheduledDirectFlights(List<Flight> scheduledFlights) {
        List<ScheduledDirectFlight> scheduledDirectFlights = new ArrayList<>();
        scheduledFlights.forEach(scheduledFlight -> scheduledDirectFlights.add(convertToScheduledDirectFlight(scheduledFlight)));
        return scheduledDirectFlights;
    }

    public List<ScheduledStopoverFlight> convertToScheduledStopoverFlights(List<Flight> scheduledFlights) {
        Map<String, List<Flight>> flightsByDepartureAirport = scheduledFlights.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));

        List<ScheduledStopoverFlight> scheduledStopoverFlights = new ArrayList<>();

        scheduledFlights.forEach(scheduledFlight -> {
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
        return scheduledStopoverFlights;
    }

    public List<ScheduledStopover> findPossibleStopoversForFlight(Flight currentFlight, Map<String, List<Flight>> flightsByDepartureAirport) {
        List<ScheduledStopover> scheduledStopovers = new ArrayList<>();
        LinkedList<Flight> stopoverFlights = findPossibleStopoversForFlight(currentFlight, flightsByDepartureAirport, Lists.newLinkedList());
        if (!stopoverFlights.isEmpty()) {
            stopoverFlights.addFirst(currentFlight);
            List<Triple<StopVisitType, String, LocalTime>> stopovers = extractStopoversFromFlights(stopoverFlights);
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

    public LinkedList<Flight> findPossibleStopoversForFlight(Flight currentFlight,
                                                       Map<String, List<Flight>> flightsByDepartureAirport, LinkedList<Flight> stopoverFlights) {
        List<Flight> destinationFlights = flightsByDepartureAirport.get(currentFlight.getArrivalStation());
        Flight foundStopoverFlight = findPresentStopoverFlight(currentFlight, destinationFlights);
        if (foundStopoverFlight != null) {
            stopoverFlights.add(foundStopoverFlight);
            findPossibleStopoversForFlight(foundStopoverFlight, flightsByDepartureAirport, stopoverFlights);
        }
        return stopoverFlights;
    }

    public Flight findPresentStopoverFlight(Flight currentFlight, List<Flight> destinationFlights) {
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
