package no.rutebanken.extime.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static no.rutebanken.extime.TestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightLegMapperTest {

    private static final int UNIQUE_FLIGHT_ID = 10;
    private static final int OTHER_UNIQUE_FLIGHT_ID = 11;

    private static final String FLIGHT_NUMBER = "FLIGHT_NUMBER";
    private static final String OTHER_FLIGHT_NUMBER = "OTHER_FLIGHT_NUMBER";

    @Test
    void map() {
        FlightLegMapper mapper = new FlightLegMapper();
        FlightEvent flightEvent1 = new FlightEvent(UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.OSL, AirportIATA.BGO, ZDT_2017_01_01, LT_00_00, LT_01_00);
        FlightEvent flightEvent2 = new FlightEvent(OTHER_UNIQUE_FLIGHT_ID, OTHER_FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.OSL, AirportIATA.TRD, ZDT_2017_01_01, LT_00_00, LT_01_00);
        List<FlightEvent> flightEvents = List.of(flightEvent1, flightEvent2);
        List<FlightLeg> flightLegs = mapper.map(flightEvents);
        assertEquals(2, flightLegs.size());
        FlightLeg first = flightLegs.getFirst();
        FlightLeg last = flightLegs.getLast();
        assertEquals(UNIQUE_FLIGHT_ID, first.getId());
        assertEquals(OTHER_UNIQUE_FLIGHT_ID, last.getId());
        assertEquals(FLIGHT_NUMBER, first.getFlightNumber());
        assertEquals(AirlineIATA.DY.name(), first.getAirlineDesignator());
        assertEquals(AirportIATA.OSL.name(), first.getDepartureAirport());
        assertEquals(AirportIATA.BGO.name(), first.getArrivalAirport());
        assertEquals(ZDT_2017_01_01.with(LT_00_00), first.getStd());
        assertEquals(ZDT_2017_01_01.with(LT_01_00), first.getSta());
    }

    @Test
    void mapWithArrivalAfterMidnight() {
        FlightLegMapper mapper = new FlightLegMapper();
        FlightEvent flightEvent1 = new FlightEvent(UNIQUE_FLIGHT_ID, FLIGHT_NUMBER, AirlineIATA.DY, AirportIATA.OSL, AirportIATA.BGO, ZDT_2017_01_01, LT_02_00, LT_01_00 );
        List<FlightEvent> flightEvents = List.of(flightEvent1);
        List<FlightLeg> flightLegs = mapper.map(flightEvents);
        assertEquals(1, flightLegs.size());
        FlightLeg first = flightLegs.getFirst();
        assertEquals(ZDT_2017_01_01.with(LT_02_00), first.getStd());
        assertEquals(ZDT_2017_01_01.plusDays(1).with(LT_01_00), first.getSta());
    }

}