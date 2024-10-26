package no.rutebanken.extime.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FlightLegMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlightLegMapper.class);


    public List<FlightLeg> map(List<FlightEvent> flightEvents) {

        LOGGER.info("Mapping {} flight events to flight legs", flightEvents.size());

        Map<Long, List<FlightEvent>> map = flightEvents.stream().collect(Collectors.groupingBy(FlightEvent::flightId, Collectors.toList()));
        map.values().forEach(events -> events.sort(Comparator.comparing(FlightEvent::scheduledTime)));

        filterInvalidEvents(map);

        List<FlightLeg> flightLegs = map.entrySet().stream().map(entry -> new FlightLegBuilder()
                        .withId(entry.getKey())
                        .withDepartureAirport(entry.getValue().getFirst().airport().name())
                        .withArrivalAirport(entry.getValue().getLast().airport().name())
                        .withFlightNumber(entry.getValue().getLast().flightNumber())
                        .withAirlineDesignator(entry.getValue().getLast().airline().name())
                        .withStd(entry.getValue().getFirst().scheduledTime())
                        .withSta(entry.getValue().getLast().scheduledTime())
                        .build())
                .toList();

        LOGGER.info("Mapped {} flight legs", flightLegs.size());

        return flightLegs;
    }

    private static void filterInvalidEvents(Map<Long, List<FlightEvent>> map) {
        map.values().removeIf(shouldHaveTwoEvents());
        map.values().removeIf(shouldHaveDepartureAndArrival());
        map.values().removeIf(shouldStartWithDeparture());
        map.values().removeIf(shouldHaveDifferentDepartureAirportandArrivalAirport());
        map.values().removeIf(shouldHaveSameFlightNumber());
        map.values().removeIf(shouldHaveSameAirline());
    }

    private static Predicate<? super List<FlightEvent>> shouldHaveSameAirline() {
        return events -> {
            boolean b = events.getFirst().airline() != events.getLast().airline();
            if(b) {
                LOGGER.warn("Events  with id {} have different airlines: {}, {}", events.getFirst().flightId(), events.getFirst().airline(), events.getLast().airline());
            }
            return b;
        };
    }


    private static Predicate<? super List<FlightEvent>> shouldHaveSameFlightNumber() {
        return events -> {
            boolean b = ! events.getFirst().flightNumber().equals(events.getLast().flightNumber());
            if(b) {
                LOGGER.warn("Events  with id {} have different flight numbers: {}, {}", events.getFirst().flightId(), events.getFirst().flightNumber(), events.getLast().flightNumber());
            }
            return b;
        };
    }



    private static Predicate<? super List<FlightEvent>> shouldHaveDifferentDepartureAirportandArrivalAirport() {
        return events -> {
            boolean b = events.getFirst().airport() == events.getLast().airport();
            if(b) {
                LOGGER.warn("Events  with id {} have same departure and arrival airports: {}", events.getLast().flightId(), events.getFirst().airport());
            }
            return b;
        };

    }

    private static Predicate<List<FlightEvent>> shouldHaveTwoEvents() {
        return events -> {
            if(events.size() == 1) {
                LOGGER.info("Found only 1 event for the flight unique id {}: {}. Possibly the other event is outside of the search window", events.getFirst().flightId(), events.getFirst());
                return true;
            }
            if(events.size() > 2) {
                LOGGER.warn("Found {} events with the same flight unique id. Expected 2. The first one is:  {} ", events.size(), events.getFirst());
                return true;
            }
            return false;
        };
    }

    private static Predicate<List<FlightEvent>> shouldHaveDepartureAndArrival() {
        return events -> {
            boolean b = events.stream().map(FlightEvent::eventType).collect(Collectors.toUnmodifiableSet()).size() < 2;
            if(b) {
                LOGGER.warn("Only {} events with id {} ", events.getFirst().eventType().name(), events.getFirst().flightId());
            }
            return b;
        };
    }

    private static Predicate<List<FlightEvent>> shouldStartWithDeparture() {
        return events -> {
            boolean b = events.getFirst().eventType() != StopVisitType.DEPARTURE;
            if(b) {
                LOGGER.warn("Events  with id {} do not start with departure", events.getFirst().flightId());
            }
            return b;
        };
    }
}
