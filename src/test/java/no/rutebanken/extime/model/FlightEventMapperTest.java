package no.rutebanken.extime.model;

import no.avinor.flydata.xjc.model.scheduled.Airport;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlightEventMapperTest {

    public static final int FLIGHT_UNIQUE_ID = 10;
    public static final String FLIGHT_NUMBER = "FLIGHT_NUMBER";
    public static final ZonedDateTime FLIGHT_SCHEDULED_TIME = ZonedDateTime.of(2024, 1, 1, 1, 1, 1, 1, ZoneId.of("CET"));

    @Test
    void map() {
        FlightEventMapper mapper = new FlightEventMapper();

        Flight flight = new Flight();
        flight.setAirport(AirportIATA.BGO.name());
        flight.setAirline(AirlineIATA.DY.name());
        flight.setArr_Dep("D");
        flight.setFlight_Id(FLIGHT_NUMBER);
        flight.setUniqueID(FLIGHT_UNIQUE_ID);
        flight.setSchedule_Time(FLIGHT_SCHEDULED_TIME);



        Flights flights = new Flights();
        flights.getFlight().add(flight);

        Airport airport = new Airport();
        airport.setName(AirportIATA.OSL.name());
        airport.getContent().add(flights);

        List<FlightEvent> flightEvents = mapper.mapToFlightEvent(airport);
        assertNotNull(flightEvents);
        assertEquals(1, flightEvents.size());
        FlightEvent actual = flightEvents.getFirst();
        assertEquals(AirportIATA.OSL, actual.airport());
        assertEquals(FLIGHT_NUMBER, actual.flightNumber());
        assertEquals(FLIGHT_UNIQUE_ID, actual.flightId());
        assertEquals(StopVisitType.DEPARTURE, actual.eventType());
        assertEquals(FLIGHT_SCHEDULED_TIME, actual.scheduledTime());
    }

}