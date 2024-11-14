package no.rutebanken.extime.model;

import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlightEventMapperTest {

    public static final int FLIGHT_UNIQUE_ID = 10;
    public static final String FLIGHT_NUMBER = "FLIGHT_NUMBER";
    public static final ZonedDateTime DATE_OF_OPERATION = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("CET"));
    public static final LocalTime STD = LocalTime.of(1,10);
    public static final LocalTime STA = LocalTime.of(2,10);


    @Test
    void map() {
        FlightEventMapper mapper = new FlightEventMapper();

        Flight flight = new Flight();
        flight.setDepartureStation(AirportIATA.OSL.name());
        flight.setArrivalStation(AirportIATA.BGO.name());
        flight.setAirlineDesignator(AirlineIATA.DY.name());
        flight.setFlightNumber(FLIGHT_NUMBER);
        flight.setId(BigInteger.valueOf(FLIGHT_UNIQUE_ID));
        flight.setDateOfOperation(DATE_OF_OPERATION);
        flight.setStd(STD);
        flight.setSta(STA);
        flight.setServiceType("J");



        Flights flights = new Flights();
        flights.getFlight().add(flight);

        List<FlightEvent> flightEvents = mapper.mapToFlightEvent(flights);
        assertNotNull(flightEvents);
        assertEquals(1, flightEvents.size());
        FlightEvent actual = flightEvents.getFirst();
        assertEquals(AirlineIATA.DY, actual.airline());
        assertEquals(AirlineIATA.DY.name() + FLIGHT_NUMBER, actual.flightNumber());
        assertEquals(FLIGHT_UNIQUE_ID, actual.flightId());

        assertEquals(AirportIATA.OSL, actual.departureAirport());
        assertEquals(AirportIATA.BGO, actual.arrivalAirport());

        assertEquals(STD, actual.departureTime());
        assertEquals(STA, actual.arrivalTime());
        assertEquals(DATE_OF_OPERATION, actual.dateOfOperation());
    }

}