package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.model.*;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component(value = "scheduledFlightConverter")
public class ScheduledFlightConverter {

    private static Set<BigInteger> UNIQUE_FLIGHT_IDS = new HashSet<>();

    @Value("${avinor.timetable.period.months}") int numberOfMonthsInPeriod;

    // @todo: use this set instead
    private Set<BigInteger> distinctFlightLegIds = new HashSet<>();

    /**
     * Ok, the idea is like this:
     *      - We have all data that we need in the original input list, both departure and arrival flights
     *      - We split up the list into 2 maps, one grouped by departure iata, and the other by arrival iata
     *      - We start iterating through the original list, flight by flight...
     *      - The main task for each flight is to find connected flights...
     *      - 1. Check if the flight unique id is in temp-cache-set (distinctFlightIds)
     *              1. if not, recursively find connections, backwards until there are no more matching flights, temp store each connected flight in i.e. a stack/queue
     *                      store in correct order in result list
     *              2. if not, recursively find connections, forward until there are no more matching flights, temp store each connected flight in i.e. a stack/queue
     *                      store in correct order in result list
     */
    public List<ScheduledFlight> convertToScheduledFlights(List<Flight> scheduledFlights) {
        // @todo: For now we create a separate from- and to date in converter, but this should actualy
        // @todo: come as input headers from previous date range generator, to be 100% sure these are always the same
        LocalDate requestPeriodFromDate = LocalDate.now(ZoneId.of("UTC"));
        LocalDate requestPeriodToDate = requestPeriodFromDate.plusMonths(numberOfMonthsInPeriod);
        OffsetTime offsetMidnight = OffsetTime.parse("00:00:00Z").withOffsetSameLocal(ZoneOffset.UTC);
        OffsetDateTime requestPeriodFromDateTime = requestPeriodFromDate.atTime(offsetMidnight);
        OffsetDateTime requestPeriodToDateTime = requestPeriodToDate.atTime(offsetMidnight);

        Map<String, List<Flight>> flightsByDepartureAirport = scheduledFlights.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));

        Map<String, List<Flight>> flightsByArrivalAirportIata = scheduledFlights.stream()
                .collect(Collectors.groupingBy(Flight::getArrivalStation));

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
                scheduledStopoverFlight.setAvailabilityPeriod(new AvailabilityPeriod(requestPeriodFromDateTime, requestPeriodToDateTime));
                scheduledStopoverFlight.setDateOfOperation(scheduledFlight.getDateOfOperation());
                scheduledStopoverFlight.getScheduledStopovers().addAll(scheduledStopovers);
                scheduledStopoverFlights.add(scheduledStopoverFlight);
            }
        }

        List<Flight> directFlights = scheduledFlights.stream()
                .filter(scheduledFlight -> !UNIQUE_FLIGHT_IDS.contains(scheduledFlight.getId()))
                .collect(Collectors.toList());
        List<ScheduledDirectFlight> scheduledDirectFlights = new ArrayList<>();
        directFlights.forEach(scheduledFlight -> scheduledDirectFlights.add(
                convertToScheduledDirectFlight(scheduledFlight, requestPeriodFromDateTime, requestPeriodToDateTime)));
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

    /**
     * Use the term flight-leg or segment, maybe rename to findMultiLegFlight
     * Consider sending the distinct id set as an argument to this method, for easier testing, and maintain by caller (main converter function)
     */
    public LinkedList<Flight> findConnectingFlightLegs(Flight currentFlightLeg, Map<String, List<Flight>> flightsByDepartureAirportIata,
                               Map<String, List<Flight>> flightsByArrivalAirportIata, Set<BigInteger> distinctFlightLegIds) {

        // 1. check if flight's unique id is already found (part of a multi-leg-flight already), and thereby present in id set
        if (distinctFlightLegIds.contains(currentFlightLeg.getId())) {
            // this means the flight leg is already part of a multi-leg flight, and found in a previous run
            // and when this check is true, the flight leg must not be processed any further, return to caller
            // what to return? null or an empty list? maybe this check should be outside of this method, and part of caller instead?
            return null;
        }

        // 2. if flight leg is not previously found as part of multi-leg flight, process it

        // 3. First of all, to find out if the current flight is part of a multi-leg flight, start by searching backwards at previous flight-legs
        //    we need a recursive function, that walks all the way back to the first flight leg, if there is any...
        //    take care! if it is not possible to find any previously connecting flight legs, we may process the first leg in order

        //    consider if we really need a linked list here (which gives overhead), we could instead sort the list by id when done finding legs

        LinkedList<Flight> previousFlightLegs = findPreviousFlightLegs(currentFlightLeg, flightsByArrivalAirportIata, Lists.newLinkedList());

        // Examples: if no previous flight is found, current flight is either first flight-leg or only the flight-leg (a direct flight)
        // after finding previous legs, and none is found, add current flight to linked list, i.e.

        // list[Flight(OSL-BGO)]

        // after finding previous flights, and one is found, add previous in order, and then current, i.e.

        // list[Flight(TRD-OSL), Flight(OSL-BGO)]

        // if more than 1 is found

        // list[Flight(TOS-TRD), Flight(TRD-OSL), Flight(OSL-BGO)]

        // 4. When we have found all connecting flight legs before current flight, and linked these together in correct order
        //    if the only element in the linked flight-leg list is the current flight, we are either processing the first flight-leg, or a direct flight.
        //    now we need to search forward at flight-legs in advance (forward), with a recursive function, going all the way to the last flight-leg in order.
        //    - if we do not find any flight-legs next after the current, and we also did not find any flight-legs previous, before the current
        //          this is a direct flight, and must not be processed any further
        //    - but if we have either previous or next flight-legs, or both, this is a multi-leg flight and must be processed as one
        //          this is a multi-leg flight, process
        //    find out where it is best to register the unique flight ids in cache set, to later be removed from original list

        LinkedList<Flight> nextFlightLegs = findNextFlightLegs(currentFlightLeg, flightsByDepartureAirportIata, Lists.newLinkedList());

        // 5. merge the 2 lists, together with the current flight leg
        //    also consider sorting the legs by id before processing further, instead of trying to insert legs in correct order.
        //    also consider changing the backing flight leg collection, do we really need a linked list? or is it enough with an arraylist or some other collection?

        List<Flight> flights = Lists.newArrayList(currentFlightLeg);
        return Lists.newLinkedList();
    }

    public LinkedList<Flight> findPreviousFlightLegs(Flight currentFlightLeg, Map<String, List<Flight>> flightsByArrivalAirportIata, LinkedList<Flight> previousFlightLegs) {
        String departureAirportIata = currentFlightLeg.getDepartureStation();
        List<Flight> flightLegsForIata = flightsByArrivalAirportIata.get(departureAirportIata);
        Predicate<Flight> flightPredicate = FlightPredicate.matchPreviousFlight(currentFlightLeg);
        Flight optionalFlightLeg = findOptionalConnectingFlightLeg(flightPredicate, flightLegsForIata);
        if (optionalFlightLeg != null) {
            previousFlightLegs.add(optionalFlightLeg);
            findPreviousFlightLegs(optionalFlightLeg, flightsByArrivalAirportIata, previousFlightLegs);
        }
        return previousFlightLegs;
    }

    public LinkedList<Flight> findNextFlightLegs(Flight currentFlightLeg, Map<String, List<Flight>> flightsByDepartureAirportIata, LinkedList<Flight> nextFlightLegs) {
        String arrivalAirportIata = currentFlightLeg.getArrivalStation();
        List<Flight> flightLegsForIata = flightsByDepartureAirportIata.get(arrivalAirportIata);
        Predicate<Flight> flightPredicate = FlightPredicate.matchNextFlight(currentFlightLeg);
        Flight optionalFlightLeg = findOptionalConnectingFlightLeg(flightPredicate, flightLegsForIata);
        if (optionalFlightLeg != null) {
            nextFlightLegs.add(optionalFlightLeg);
            findNextFlightLegs(optionalFlightLeg, flightsByDepartureAirportIata, nextFlightLegs);
        }
        return nextFlightLegs;
    }

    public Flight findOptionalConnectingFlightLeg(Predicate<Flight> flightPredicate, List<Flight> flightLegs) {
        Optional<Flight> optionalStopoverFlight = flightLegs.stream()
                .filter(flightPredicate)
                .findFirst();
        if (optionalStopoverFlight.isPresent()) {
            return optionalStopoverFlight.get();
        }
        return null;
    }

    /**
     * Use the term Multi-Leg-Flights or connecting flights
     */
    public boolean isMultiLegFlight(Flight currentFlight) {
        return false;
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

            List<Triple<StopVisitType, String, OffsetTime>> stopovers = extractStopoversFromFlights(stopoverFlights);
            Triple<StopVisitType, String, OffsetTime> tempArrivalStopover = null;
            for (ListIterator<Triple<StopVisitType, String, OffsetTime>> it = stopovers.listIterator(); it.hasNext(); ) {
                Triple<StopVisitType, String, OffsetTime> stopover = it.next();
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

    public List<Triple<StopVisitType, String, OffsetTime>> extractStopoversFromFlights(List<Flight> stopoverFlights) {
        List<Triple<StopVisitType, String, OffsetTime>> stopoverTriples = new ArrayList<>();
        stopoverFlights.forEach(stopoverFlight -> {
            Triple<StopVisitType, String, OffsetTime> departureTriple = new ImmutableTriple<>(
                    StopVisitType.DEPARTURE, stopoverFlight.getDepartureStation(), stopoverFlight.getStd());

            Triple<StopVisitType, String, OffsetTime> arrivalTriple = new ImmutableTriple<>(
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

    public ScheduledDirectFlight convertToScheduledDirectFlight(Flight scheduledFlight, OffsetDateTime fromDateTime, OffsetDateTime toDateTime) {
        ScheduledDirectFlight scheduledDirectFlight = new ScheduledDirectFlight();
        scheduledDirectFlight.setFlightId(scheduledFlight.getId());
        scheduledDirectFlight.setAirlineIATA(scheduledFlight.getAirlineDesignator());
        scheduledDirectFlight.setAirlineFlightId(String.format("%s%s", scheduledFlight.getAirlineDesignator(), scheduledFlight.getFlightNumber()));
        scheduledDirectFlight.setAvailabilityPeriod(new AvailabilityPeriod(fromDateTime, toDateTime));
        scheduledDirectFlight.setDateOfOperation(scheduledFlight.getDateOfOperation());
        scheduledDirectFlight.setDepartureAirportIATA(scheduledFlight.getDepartureStation());
        scheduledDirectFlight.setArrivalAirportIATA(scheduledFlight.getArrivalStation());
        scheduledDirectFlight.setTimeOfDeparture(scheduledFlight.getStd());
        scheduledDirectFlight.setTimeOfArrival(scheduledFlight.getSta());
        return scheduledDirectFlight;
    }

}
