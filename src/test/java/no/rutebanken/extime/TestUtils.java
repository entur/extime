package no.rutebanken.extime;

import no.rutebanken.extime.model.*;

import java.time.LocalTime;
import java.time.ZonedDateTime;

public class TestUtils {

    public static final ZonedDateTime ZDT_2017_01_01 = ZonedDateTime.parse("2017-01-01T00:00:00Z");

    public static final LocalTime LT_00_00 = LocalTime.of(0, 0, 0);
    public static final LocalTime LT_01_00 = LocalTime.of(1, 0, 0);
    public static final LocalTime LT_02_00 = LocalTime.of(2, 0, 0);



    public static final ZonedDateTime ZDT_2017_01_01_00_00 = ZonedDateTime.parse("2017-01-01T00:00:00Z");



    public static final ZonedDateTime ZDT_2017_01_01_23_59 = ZonedDateTime.parse("2017-01-01T23:59:00Z");

    public static final ZonedDateTime ZDT_2017_01_01_06_00 = ZonedDateTime.parse("2017-01-01T06:00:00Z");
    public static final ZonedDateTime ZDT_2017_01_01_06_30 = ZonedDateTime.parse("2017-01-01T06:30:00Z");
    public static final ZonedDateTime ZDT_2017_01_01_07_00 = ZonedDateTime.parse("2017-01-01T07:00:00Z");
    public static final ZonedDateTime ZDT_2017_01_01_07_30 = ZonedDateTime.parse("2017-01-01T07:30:00Z");
    public static final ZonedDateTime ZDT_2017_01_01_08_00 = ZonedDateTime.parse("2017-01-01T08:00:00Z");
    public static final ZonedDateTime ZDT_2017_01_01_08_30 = ZonedDateTime.parse("2017-01-01T08:30:00Z");
    public static final ZonedDateTime ZDT_2017_01_01_09_00 = ZonedDateTime.parse("2017-01-01T09:00:00Z");
    public static final ZonedDateTime ZDT_2017_01_01_09_30 = ZonedDateTime.parse("2017-01-01T09:30:00Z");
    public static final ZonedDateTime ZDT_2017_01_01_10_00 = ZonedDateTime.parse("2017-01-01T10:00:00Z");
    public static final ZonedDateTime ZDT_2017_01_01_10_30 = ZonedDateTime.parse("2017-01-01T10:30:00Z");

    public static final ZonedDateTime ZDT_2017_01_02_08_00 = ZonedDateTime.parse("2017-01-02T08:00:00Z");
    public static final ZonedDateTime ZDT_2017_01_02_08_30 = ZonedDateTime.parse("2017-01-02T08:30:00Z");
    public static final ZonedDateTime ZDT_2017_01_03_08_00 = ZonedDateTime.parse("2017-01-03T08:00:00Z");
    public static final ZonedDateTime ZDT_2017_01_03_08_30 = ZonedDateTime.parse("2017-01-03T08:30:00Z");

    public static  FlightLeg createFlightLeg(long id, String designator, String flightNumber,
                                             String departureStation, ZonedDateTime departureTime, String arrivalStation, ZonedDateTime arrivalTime) {
        return new FlightLegBuilder()
                .withId(id)
                .withDepartureAirport(departureStation)
                .withArrivalAirport(arrivalStation)
                .withFlightNumber(flightNumber)
                .withAirlineDesignator(designator)
                .withSta(arrivalTime)
                .withStd(departureTime)
                .build();
    }
}
