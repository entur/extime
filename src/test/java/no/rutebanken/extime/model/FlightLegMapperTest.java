package no.rutebanken.extime.model;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
class FlightLegMapperTest {

    private static final int UNIQUE_FLIGHT_ID = 10;
    private static final int OTHER_UNIQUE_FLIGHT_ID = 11;

    private static final String FLIGHT_NUMBER = "FLIGHT_NUMBER";
    private static final String OTHER_FLIGHT_NUMBER = "OTHER_FLIGHT_NUMBER";

    private static final ZonedDateTime FLIGHT_SCHEDULED_DEPARTURE_TIME = ZonedDateTime.of(2024, 1, 1, 1, 0, 0, 0, ZoneId.of("CET"));
    private static final ZonedDateTime FLIGHT_SCHEDULED_ARRIVAL_TIME = ZonedDateTime.of(2024, 1, 1, 2, 0, 0, 0, ZoneId.of("CET"));

    private static final ZonedDateTime FLIGHT_SCHEDULED_ARRIVAL_TIME_BEFORE_DEPARTURE = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("CET"));

    @Test
    void mapEvents() {
        FlightLegMapper mapper = new FlightLegMapper();
        FlightEvent flightEvent1 = new FlightEvent(StopVisitType.DEPARTURE, UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.OSL, FLIGHT_SCHEDULED_DEPARTURE_TIME);
        FlightEvent flightEvent2 = new FlightEvent(StopVisitType.ARRIVAL, UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.BGO, FLIGHT_SCHEDULED_ARRIVAL_TIME);
        List<FlightEvent> flightEvents = List.of(flightEvent1, flightEvent2);
        List<FlightLeg> flightLegs = mapper.map(flightEvents);
        assertEquals(1, flightLegs.size());
        FlightLeg flightLeg = flightLegs.getFirst();
        assertEquals(FLIGHT_NUMBER, flightLeg.getFlightNumber());
        assertEquals(UNIQUE_FLIGHT_ID,flightLeg.getId());
        assertEquals(AirportIATA.OSL.name(), flightLeg.getDepartureAirport());
        assertEquals(AirportIATA.BGO.name(), flightLeg.getArrivalAirport());
        assertEquals(FLIGHT_SCHEDULED_DEPARTURE_TIME, flightLeg.getStd());
        assertEquals(FLIGHT_SCHEDULED_ARRIVAL_TIME, flightLeg.getSta());
    }

    @Test
    void mapMismatchedId() {
        FlightLegMapper mapper = new FlightLegMapper();
        FlightEvent flightEvent1 = new FlightEvent(StopVisitType.DEPARTURE, UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.OSL, FLIGHT_SCHEDULED_DEPARTURE_TIME);
        FlightEvent flightEvent2 = new FlightEvent(StopVisitType.ARRIVAL, OTHER_UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.BGO, FLIGHT_SCHEDULED_ARRIVAL_TIME);
        List<FlightEvent> flightEvents = List.of(flightEvent1, flightEvent2);
        List<FlightLeg> flightLegs = mapper.map(flightEvents);
        assertTrue(flightLegs.isEmpty());
    }

    @Test
    void mapMismatchedAirline() {
        FlightLegMapper mapper = new FlightLegMapper();
        FlightEvent flightEvent1 = new FlightEvent(StopVisitType.DEPARTURE, UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.OSL, FLIGHT_SCHEDULED_DEPARTURE_TIME);
        FlightEvent flightEvent2 = new FlightEvent(StopVisitType.ARRIVAL, UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.D8, AirportIATA.BGO, FLIGHT_SCHEDULED_ARRIVAL_TIME);
        List<FlightEvent> flightEvents = List.of(flightEvent1, flightEvent2);
        List<FlightLeg> flightLegs = mapper.map(flightEvents);
        assertTrue(flightLegs.isEmpty());
    }

    @Test
    void mapMismatchedAFlightNumber() {
        FlightLegMapper mapper = new FlightLegMapper();
        FlightEvent flightEvent1 = new FlightEvent(StopVisitType.DEPARTURE, UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.OSL, FLIGHT_SCHEDULED_DEPARTURE_TIME);
        FlightEvent flightEvent2 = new FlightEvent(StopVisitType.ARRIVAL, UNIQUE_FLIGHT_ID, OTHER_FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.BGO, FLIGHT_SCHEDULED_ARRIVAL_TIME);
        List<FlightEvent> flightEvents = List.of(flightEvent1, flightEvent2);
        List<FlightLeg> flightLegs = mapper.map(flightEvents);
        assertTrue(flightLegs.isEmpty());
    }

    @Test
    void mapMissingEvent() {
        FlightLegMapper mapper = new FlightLegMapper();
        FlightEvent flightEvent1 = new FlightEvent(StopVisitType.DEPARTURE, UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.OSL, FLIGHT_SCHEDULED_DEPARTURE_TIME);
        List<FlightEvent> flightEvents = List.of(flightEvent1);
        List<FlightLeg> flightLegs = mapper.map(flightEvents);
        assertTrue(flightLegs.isEmpty());
    }

    @Test
    void mapMissingDeparture() {
        FlightLegMapper mapper = new FlightLegMapper();
        FlightEvent flightEvent1 = new FlightEvent(StopVisitType.DEPARTURE, UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.OSL, FLIGHT_SCHEDULED_DEPARTURE_TIME);
        FlightEvent flightEvent2 = new FlightEvent(StopVisitType.DEPARTURE, UNIQUE_FLIGHT_ID, OTHER_FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.BGO, FLIGHT_SCHEDULED_ARRIVAL_TIME);
        List<FlightEvent> flightEvents = List.of(flightEvent1, flightEvent2);
        List<FlightLeg> flightLegs = mapper.map(flightEvents);
        assertTrue(flightLegs.isEmpty());
    }

    @Test
    void mapArrivalBeforeDeparture() {
        FlightLegMapper mapper = new FlightLegMapper();
        FlightEvent flightEvent1 = new FlightEvent(StopVisitType.DEPARTURE, UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.OSL, FLIGHT_SCHEDULED_DEPARTURE_TIME);
        FlightEvent flightEvent2 = new FlightEvent(StopVisitType.ARRIVAL, UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.BGO, FLIGHT_SCHEDULED_ARRIVAL_TIME_BEFORE_DEPARTURE);
        List<FlightEvent> flightEvents = List.of(flightEvent1, flightEvent2);
        List<FlightLeg> flightLegs = mapper.map(flightEvents);
        assertTrue(flightLegs.isEmpty());
    }

    @Test
    void mapSameDepartureAndArrivalAirport() {
        FlightLegMapper mapper = new FlightLegMapper();
        FlightEvent flightEvent1 = new FlightEvent(StopVisitType.DEPARTURE, UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.OSL, FLIGHT_SCHEDULED_DEPARTURE_TIME);
        FlightEvent flightEvent2 = new FlightEvent(StopVisitType.ARRIVAL, UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.OSL, FLIGHT_SCHEDULED_ARRIVAL_TIME);
        List<FlightEvent> flightEvents = List.of(flightEvent1, flightEvent2);
        List<FlightLeg> flightLegs = mapper.map(flightEvents);
        assertTrue(flightLegs.isEmpty());
    }


  
}