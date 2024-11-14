package no.rutebanken.extime.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.*;

/**
 * Map FlightEvents to a sorted list of FlightLegs.
 * STD and STA are mapped to ZonedDateTime to simplify time calculations.
 */
public class FlightLegMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlightLegMapper.class);


    public List<FlightLeg> map(List<FlightEvent> flightEvents) {

        LOGGER.info("Mapping {} flight events to flight legs", flightEvents.size());

        List<FlightLeg> flightLegs = flightEvents.stream().map(flightEvent -> new FlightLegBuilder()
                        .withId(flightEvent.flightId())
                        .withDepartureAirport(flightEvent.departureAirport().name())
                        .withArrivalAirport(flightEvent.arrivalAirport().name())
                        .withFlightNumber(flightEvent.flightNumber())
                        .withAirlineDesignator(flightEvent.airline().name())
                        .withStd(flightEvent.dateOfOperation().with(flightEvent.departureTime()))
                        .withSta(toSta(flightEvent))
                        .build())
                .sorted(Comparator.comparing(FlightLeg::getStd))
                .toList();

        LOGGER.info("Mapped {} flight legs", flightLegs.size());

        return flightLegs;
    }

    private static ZonedDateTime toSta(FlightEvent flightEvent) {
        ZonedDateTime sta = flightEvent.dateOfOperation().with(flightEvent.arrivalTime());
        if(flightEvent.arrivalTime().isBefore(flightEvent.departureTime())) {
            // adjust date for flights landing after midnight
            return sta.plusDays(1);
        }
        return sta;
    }

}
