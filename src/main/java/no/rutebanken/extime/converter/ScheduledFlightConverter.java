package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.model.*;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component(value = "scheduledFlightConverter")
public class ScheduledFlightConverter {

    private static Set<BigInteger> UNIQUE_FLIGHT_IDS = new HashSet<>();

    public List<ScheduledFlight> convertToScheduledFlights(List<Flight> scheduledFlights) {
        Map<String, List<Flight>> flightsByDepartureAirport = scheduledFlights.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));
        List<ScheduledStopoverFlight> scheduledStopoverFlights = new ArrayList<>();

        for (Flight scheduledFlight : scheduledFlights) {
            // @todo: we probably need this here instead, and then send the result to next findPossibleStopoversForFlight method
            // @todo: we need this to be able to store ids of flights belonging to a flight with stopovers
            // LinkedList<Flight> stopoverFlights = findPossibleStopoversForFlight(currentFlight, flightsByDepartureAirport, Lists.newLinkedList());

            List<ScheduledStopover> scheduledStopovers = findPossibleStopoversForFlight(scheduledFlight, flightsByDepartureAirport);
            if (!scheduledStopovers.isEmpty()) {
                ScheduledStopoverFlight scheduledStopoverFlight = new ScheduledStopoverFlight();
                scheduledStopoverFlight.setAirlineFlightId(String.format("%s%s",
                        scheduledFlight.getAirlineDesignator(), scheduledFlight.getFlightNumber()));
                scheduledStopoverFlight.setAirlineIATA(scheduledFlight.getAirlineDesignator());
                scheduledStopoverFlight.setDateOfOperation(scheduledFlight.getDateOfOperation());
                scheduledStopoverFlight.getScheduledStopovers().addAll(scheduledStopovers);
                scheduledStopoverFlights.add(scheduledStopoverFlight);
            }
        }

        List<Flight> directFlights = scheduledFlights.stream()
                .filter(scheduledFlight -> !UNIQUE_FLIGHT_IDS.contains(scheduledFlight.getId()))
                .collect(Collectors.toList());
        List<ScheduledDirectFlight> scheduledDirectFlights = new ArrayList<>();
        directFlights.forEach(scheduledFlight -> scheduledDirectFlights.add(convertToScheduledDirectFlight(scheduledFlight)));
        Map<String, List<ScheduledStopoverFlight>> stopoverFlightsByFlightId = scheduledStopoverFlights.stream()
                .sorted(Comparator.comparing(ScheduledStopoverFlight::getDateOfOperation))
                .collect(Collectors.groupingBy(ScheduledStopoverFlight::getAirlineFlightId));
        removeSubRoutes(stopoverFlightsByFlightId);
        Map<String, List<ScheduledDirectFlight>> directFlightsByFlightId = scheduledDirectFlights.stream()
                .sorted(Comparator.comparing(ScheduledDirectFlight::getDateOfOperation))
                .collect(Collectors.groupingBy(ScheduledDirectFlight::getAirlineFlightId));

        List<ScheduledFlight> distinctFlights = new ArrayList<>();

        for (Map.Entry<String, List<ScheduledStopoverFlight>> entry : stopoverFlightsByFlightId.entrySet()) {
            String flightId = entry.getKey();
            List<ScheduledStopoverFlight> flights = entry.getValue();
            Set<DayOfWeek> daysOfWeek = findJourneyPatterns(flightId, flights);
            ScheduledFlight scheduledFlight = flights.get(0);
            scheduledFlight.setWeekDaysPattern(daysOfWeek);
            distinctFlights.add(scheduledFlight);
        }
        for (Map.Entry<String, List<ScheduledDirectFlight>> entry : directFlightsByFlightId.entrySet()) {
            String flightId = entry.getKey();
            List<ScheduledDirectFlight> flights = entry.getValue();
            Set<DayOfWeek> daysOfWeek = findJourneyPatterns(flightId, flights);
            ScheduledFlight scheduledFlight = flights.get(0);
            scheduledFlight.setWeekDaysPattern(daysOfWeek);
            distinctFlights.add(scheduledFlight);
        }
        return distinctFlights;
    }

    private void removeSubRoutes(Map<String, List<ScheduledStopoverFlight>> stopoverFlightsByFlightId) {
        stopoverFlightsByFlightId.forEach((flightId, flights) -> {
            int maxStopovers = findMaxStopovers(flights);
            if (maxStopovers > 0) {
                List<ScheduledStopoverFlight> filteredFlights = flights.stream()
                        .filter(flight -> flight.getScheduledStopovers().size() == maxStopovers)
                        .collect(Collectors.toList());
                stopoverFlightsByFlightId.replace(flightId, filteredFlights);
            }
        });
    }

    private int findMaxStopovers(List<ScheduledStopoverFlight> flights) {
        Optional<ScheduledStopoverFlight> firstFlightWithMostStopvers = flights.stream()
                .collect(Collectors.maxBy((flight1, flight2) -> {
                    final int flight1Stopvers = flight1.getScheduledStopovers().size();
                    final int flight2Stopvers = flight2.getScheduledStopovers().size();
                    return flight1Stopvers - flight2Stopvers;
                }));
        if (firstFlightWithMostStopvers.isPresent()) {
            return firstFlightWithMostStopvers.get().getScheduledStopovers().size();
        }
        return 0;
    }

    /**
     * @todo: handle different departure times, i.e. on weekends (will be used as separate ServiceJourneys in netex)
     *
     * For example, flight WF149 between Oslo and Bergen, operates every work day (man-fri) with departure time 14:05
     * but also one day at the weekend, sunday, with departure time at 14:00
     *
     * Also, one flight can operate with one route (i.e. OSL-HOV-SOG-BGO) some days, and another route (i.e. OSL-HOV-BGO) some other day, support!
     */
    private Set<DayOfWeek> findJourneyPatterns(String flightId, List<? extends ScheduledFlight> flights) {
        SortedSet<DayOfWeek> daysOfWeek = new TreeSet<>();
        LocalDate periodStartDate = flights.get(0).getDateOfOperation();
        LocalDate periodEndDate = periodStartDate.plusWeeks(1L);
        for (ScheduledFlight flight : flights) {
            if (flight.getDateOfOperation().isAfter(periodEndDate)) {
                break;
            } else {
                LocalDate dateOfOperation = flight.getDateOfOperation();
                DayOfWeek dayOfWeek = dateOfOperation.getDayOfWeek();
                daysOfWeek.add(dayOfWeek);
            }
        }
        return daysOfWeek;
    }

    // @todo: instead of creating a flight-route for each subroute, and add it to the result,
    // @todo: find a way to prevent subroutes to be created at all,
    // @todo: i.e. keep track of flights added to a route and check against this collection for each new flight
    public List<ScheduledStopover> findPossibleStopoversForFlight(Flight currentFlight, Map<String, List<Flight>> flightsByDepartureAirport) {
        List<ScheduledStopover> scheduledStopovers = new ArrayList<>();

        // @todo: consider extracting this statement to outside of this method, so we can get a hold of all flight unique ids before more processing.
        LinkedList<Flight> stopoverFlights = findPossibleStopoversForFlight(currentFlight, flightsByDepartureAirport, Lists.newLinkedList());
        if (!stopoverFlights.isEmpty()) {
            stopoverFlights.addFirst(currentFlight);

            // @todo: fix this bug, it is not correct to do this here, we need to capture subflights also
            // @todo: as part of a main flight, even when the subflight does not have any stopovers
            Set<BigInteger> uniqueFlightIds = stopoverFlights.stream()
                    .map(Flight::getId)
                    .collect(Collectors.toSet());
            uniqueFlightIds.forEach(uniqueId -> UNIQUE_FLIGHT_IDS.add(uniqueId));

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

    public LinkedList<Flight> findPossibleStopoversForFlight(Flight currentFlight, Map<String, List<Flight>> flightsByDepartureAirport,
                                                             LinkedList<Flight> stopoverFlights) {
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
