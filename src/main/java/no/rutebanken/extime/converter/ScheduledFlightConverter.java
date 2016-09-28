package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.model.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component(value = "scheduledFlightConverter")
public class ScheduledFlightConverter {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledFlightConverter.class);

    @Value("${avinor.timetable.period.months}") int numberOfMonthsInPeriod;

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

        Set<BigInteger> distinctFlightLegIds = Sets.newHashSet();
        List<ScheduledStopoverFlight> scheduledStopoverFlights = Lists.newArrayList();
        List<ScheduledDirectFlight> scheduledDirectFlights = Lists.newArrayList();
        List<ScheduledFlight> distinctFlights = Lists.newArrayList();

        for (Flight flight : scheduledFlights) {
            List<Flight> connectingFlightLegs = findConnectingFlightLegs(flight, flightsByDepartureAirport, flightsByArrivalAirportIata, distinctFlightLegIds);
            if (CollectionUtils.isEmpty(connectingFlightLegs)) {
/*
                logger.debug("Flight with unique id: {}, and flightId: {} is part of multi leg flight, and already processed, skipping...",
                        flight.getId(),
                        flight.getAirlineDesignator(),
                        flight.getFlightNumber());
*/
                continue;
            }
            if (isMultiLegFlightRoute(connectingFlightLegs)) {
                List<Triple<StopVisitType, String, OffsetTime>> stopovers = extractStopoversFromFlights(connectingFlightLegs);
                List<ScheduledStopover> scheduledStopovers = createScheduledStopovers(stopovers);
                ScheduledStopoverFlight scheduledStopoverFlight = createScheduledStopoverFlight(requestPeriodFromDateTime, requestPeriodToDateTime, flight, scheduledStopovers);
                if (scheduledStopoverFlight != null) {
                    scheduledStopoverFlights.add(scheduledStopoverFlight);
                }
            } else if (isDirectFlightRoute(connectingFlightLegs)) {
                ScheduledDirectFlight directFlight = convertToScheduledDirectFlight(flight, requestPeriodFromDateTime, requestPeriodToDateTime);
                scheduledDirectFlights.add(directFlight);
            } else {
                logger.error("Flight with unique id: {}, and flightId: {} is NOT a valid flight",
                        flight.getId(),
                        flight.getAirlineDesignator(),
                        flight.getFlightNumber());
                // @todo: throw a more specific exception type here
                throw new RuntimeException("Invalid flight");
            }
        }

        Map<String, List<ScheduledStopoverFlight>> stopoverFlightsByFlightId = scheduledStopoverFlights.stream()
                .sorted(Comparator.comparing(ScheduledStopoverFlight::getDateOfOperation))
                .collect(Collectors.groupingBy(ScheduledStopoverFlight::getAirlineFlightId));

        Map<String, List<ScheduledDirectFlight>> directFlightsByFlightId = scheduledDirectFlights.stream()
                .sorted(Comparator.comparing(ScheduledDirectFlight::getDateOfOperation))
                .collect(Collectors.groupingBy(ScheduledDirectFlight::getAirlineFlightId));

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

    public List<ScheduledStopover> createScheduledStopovers(List<Triple<StopVisitType, String, OffsetTime>> stopovers) {
        List<ScheduledStopover> multiLegFlights = Lists.newArrayList();
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
                multiLegFlights.add(scheduledStopover);
            } else {
                if (!it.hasNext()) {
                    ScheduledStopover scheduledStopover = new ScheduledStopover();
                    scheduledStopover.setAirportIATA(stopover.getMiddle());
                    scheduledStopover.setArrivalTime(stopover.getRight());
                    multiLegFlights.add(scheduledStopover);
                } else {
                    tempArrivalStopover = stopover;
                }
            }
        }
        return multiLegFlights;
    }

    public ScheduledStopoverFlight createScheduledStopoverFlight(OffsetDateTime requestPeriodFromDateTime, OffsetDateTime requestPeriodToDateTime,
                                                                 Flight currentFlight, List<ScheduledStopover> scheduledStopovers) {
        if (CollectionUtils.isNotEmpty(scheduledStopovers)) {
            ScheduledStopoverFlight scheduledStopoverFlight = new ScheduledStopoverFlight();
            scheduledStopoverFlight.setAirlineFlightId(String.format("%s%s",
                    currentFlight.getAirlineDesignator(), currentFlight.getFlightNumber()));
            scheduledStopoverFlight.setAirlineIATA(currentFlight.getAirlineDesignator());
            scheduledStopoverFlight.setAvailabilityPeriod(new AvailabilityPeriod(requestPeriodFromDateTime, requestPeriodToDateTime));
            scheduledStopoverFlight.setDateOfOperation(currentFlight.getDateOfOperation());
            scheduledStopoverFlight.getScheduledStopovers().addAll(scheduledStopovers);
            return scheduledStopoverFlight;
        }
        return null;
    }

    public boolean isMultiLegFlightRoute(List<Flight> flights) {
        return CollectionUtils.isNotEmpty(flights) && flights.size() > 1;
    }

    public boolean isDirectFlightRoute(List<Flight> flights) {
        return CollectionUtils.isNotEmpty(flights) && flights.size() == 1;
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

    // TODO: We must take into consideration that it is possible to have multiple journey patterns or routes for a specific flight id
    // like flight WF739 which can take the following route/journey pattern one day: BOO-OSY-TRD, and this route/journey pattern another day: BOO-MJF-OSY-TRD
    public List<Flight> findConnectingFlightLegs(Flight currentFlightLeg, Map<String, List<Flight>> flightsByDepartureAirportIata,
                               Map<String, List<Flight>> flightsByArrivalAirportIata, Set<BigInteger> distinctFlightLegIds) {
        if (distinctFlightLegIds.contains(currentFlightLeg.getId())) {
            return Collections.emptyList();
        }
        List<Flight> connectingFlightLegs = Lists.newArrayList(currentFlightLeg);
        List<Flight> previousFlightLegs = findPreviousFlightLegs(currentFlightLeg, flightsByArrivalAirportIata, Lists.newArrayList());
        if (CollectionUtils.isNotEmpty(previousFlightLegs)) {
            connectingFlightLegs.addAll(previousFlightLegs);
        }
        List<Flight> nextFlightLegs = findNextFlightLegs(currentFlightLeg, flightsByDepartureAirportIata, Lists.newArrayList());
        if (CollectionUtils.isNotEmpty(nextFlightLegs)) {
            connectingFlightLegs.addAll(nextFlightLegs);
        }
        if (connectingFlightLegs.size() > 1) {
            Collections.sort(connectingFlightLegs, (flight1, flight2) -> flight1.getId().compareTo(flight2.getId()));
            connectingFlightLegs.forEach(flight -> distinctFlightLegIds.add(flight.getId()));
        }
        return connectingFlightLegs;
    }

    public List<Flight> findPreviousFlightLegs(Flight currentFlightLeg, Map<String, List<Flight>> flightsByArrivalAirportIata, List<Flight> previousFlightLegs) {
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

    public List<Flight> findNextFlightLegs(Flight currentFlightLeg, Map<String, List<Flight>> flightsByDepartureAirportIata, List<Flight> nextFlightLegs) {
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
