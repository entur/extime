package no.rutebanken.extime.fixtures;

import com.google.common.collect.Lists;
import no.rutebanken.extime.model.AvailabilityPeriod;
import no.rutebanken.extime.model.FlightRoute;
import no.rutebanken.extime.model.LineDataSet;
import no.rutebanken.extime.model.ScheduledFlight;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.extime.Constants.OFFSET_MIDNIGHT_UTC;

public final class LineDataSetFixture {

    private LineDataSetFixture() {}

    public static LineDataSet createEmptyLineDataSet() {
        return new LineDataSet();
    }

    public static LineDataSet createLineDataSet(String airlineIata, String airlineName,
            String lineDesignation, String lineName) {
        LineDataSet lineDataSet = createEmptyLineDataSet();
        lineDataSet.setAirlineIata(airlineIata);
        lineDataSet.setAirlineName(airlineName);
        lineDataSet.setLineDesignation(lineDesignation);
        lineDataSet.setLineName(lineName);
        return lineDataSet;
    }

    public static AvailabilityPeriod createAvailabilityPeriod(LocalDate periodFromDate, LocalDate periodToDate) {
        OffsetTime offsetMidnight = OffsetTime.parse(OFFSET_MIDNIGHT_UTC).withOffsetSameLocal(ZoneOffset.UTC);
        OffsetDateTime requestPeriodFromDateTime = periodFromDate.atTime(offsetMidnight);
        OffsetDateTime requestPeriodToDateTime = periodToDate.atTime(offsetMidnight);
        return new AvailabilityPeriod(requestPeriodFromDateTime, requestPeriodToDateTime);
    }

    private static ScheduledFlight createScheduledDirectFlight(String airlineIATA, String airlineFlightId,
                                                        LocalDate dateOfOperation, String departureAirportIata, String departureAirportName,
                                                        String arrivalAirportIata, String arrivalAirportName, OffsetTime timeOfDeparture, OffsetTime timeOfArrival) {

        ScheduledFlight scheduledFlight = new ScheduledFlight();
        scheduledFlight.setAirlineIATA(airlineIATA);
        scheduledFlight.setAirlineFlightId(airlineFlightId);
        scheduledFlight.setDateOfOperation(dateOfOperation);
        scheduledFlight.setDepartureAirportIATA(departureAirportIata);
        scheduledFlight.setDepartureAirportName(departureAirportName);
        scheduledFlight.setArrivalAirportIATA(arrivalAirportIata);
        scheduledFlight.setArrivalAirportName(arrivalAirportName);
        scheduledFlight.setTimeOfDeparture(timeOfDeparture);
        scheduledFlight.setTimeOfArrival(timeOfArrival);
        return scheduledFlight;
    }

}
