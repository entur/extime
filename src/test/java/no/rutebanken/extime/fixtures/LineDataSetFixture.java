package no.rutebanken.extime.fixtures;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import no.rutebanken.extime.model.*;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tomcat.jni.Local;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.extime.Constants.*;

public final class LineDataSetFixture {

    private static final Map<String, String> airports = new HashMap<>();
    private static final Map<String, String> airlines = new HashMap<>();

    static {
        // init airports
        airports.put("OSL", "Oslo");
        airports.put("BGO", "Bergen");
        airports.put("TRD", "Trondheim");
        airports.put("SOG", "Sogndal");
        airports.put("MOL", "Molde");

        // init airlines
        airlines.put("DY", "Norwegian");
        airlines.put("SK", "SAS");
        airlines.put("WF", "Widerøe");
    }

    private LineDataSetFixture() {
    }

    public static String getAirportName(String iata) {
        return airports.containsKey(iata) ? airports.get(iata) : null;
    }

    public static String getAirlineName(String iata) {
        return airlines.containsKey(iata) ? airlines.get(iata) : null;
    }

    public static LineDataSet createEmptyLineDataSet() {
        return new LineDataSet();
    }

    public static LineDataSet createBasicLineDataSet(String airlineIata, String lineDesignation) {
        LineDataSet lineDataSet = createEmptyLineDataSet();
        lineDataSet.setAirlineIata(airlineIata);
        lineDataSet.setAirlineName(airlines.get(airlineIata));
        lineDataSet.setLineDesignation(lineDesignation);
        lineDataSet.setLineName(resolveLineName(lineDesignation));
        return lineDataSet;
    }

    public static LineDataSet createLineDataSetWithFixedDates(String airlineIata, String lineDesignation, List<Pair<String, List<LocalDate>>> routeJourneyPairs, LocalTime fixedTimeOfDeparture) {
        LineDataSet lineDataSet = LineDataSetFixture.createBasicLineDataSet(airlineIata, lineDesignation);

        // init period
        LocalDateTime periodFromDate = LocalDate.now().atStartOfDay();
        LocalDateTime periodToDate = periodFromDate.plusMonths(1);


        AvailabilityPeriod availabilityPeriod = new AvailabilityPeriod(periodFromDate, periodToDate);
        lineDataSet.setAvailabilityPeriod(availabilityPeriod);

        // init routes and journeys
        List<FlightRoute> routes = new ArrayList<>();
        Map<String, Map<String, List<ScheduledFlight>>> routeJourneys = new HashMap<>();

        for (Pair<String, List<LocalDate>> pair : routeJourneyPairs) {
            Map<String, List<ScheduledFlight>> flightsById = new HashMap<>();
            List<ScheduledFlight> flights = new ArrayList<>();

            String routeDesignation = pair.getKey();
            routes.add(new FlightRoute(routeDesignation, resolveRouteName(routeDesignation)));

            for (LocalDate dateOfOperation : pair.getValue()) {
                ScheduledFlight scheduledFlight = new ScheduledFlight();
                scheduledFlight.setAirlineIATA(airlineIata);

                String airlineFlightId = airlineIata + generateRandomId(100, 9999);
                scheduledFlight.setAirlineFlightId(airlineFlightId);
                scheduledFlight.setDateOfOperation(dateOfOperation);

                List<String> routeStops = resolveStops(routeDesignation);
                if (routeStops.size() == 2) {
                    String departureAirportIata = routeStops.get(0);
                    scheduledFlight.setDepartureAirportIATA(departureAirportIata);
                    scheduledFlight.setDepartureAirportName(airports.get(departureAirportIata));

                    String arrivalAirportIata = routeStops.get(1);
                    scheduledFlight.setArrivalAirportIATA(arrivalAirportIata);
                    scheduledFlight.setArrivalAirportName(airports.get(arrivalAirportIata));

                    LocalTime timeOfDeparture;
                    if (fixedTimeOfDeparture == null) {
                        timeOfDeparture = generateOffsetTime();
                    } else {
                         timeOfDeparture = fixedTimeOfDeparture;
                    }
                    scheduledFlight.setTimeOfDeparture(timeOfDeparture);

                    LocalTime timeOfArrival = timeOfDeparture.plusHours(1);
                    scheduledFlight.setTimeOfArrival(timeOfArrival);
                } else if (routeStops.size() > 2) {
                    List<ScheduledStopover> stopovers = Lists.newArrayList();
                    LocalTime timeOfArrival = generateOffsetTime();
                    LocalTime timeOfDeparture;

                    for (int j = 0; j < routeStops.size(); j++) {
                        ScheduledStopover stopover = new ScheduledStopover();
                        stopover.setAirportIATA(routeStops.get(j));

                        if (j > 0) {
                            stopover.setArrivalTime(timeOfArrival);
                        }
                        if (j < routeStops.size() - 1) {
                            timeOfDeparture = timeOfArrival.plusHours(1);
                            stopover.setDepartureTime(timeOfDeparture);
                            timeOfArrival = timeOfDeparture.plusHours(1);
                        }

                        stopovers.add(stopover);
                    }
                    scheduledFlight.getScheduledStopovers().addAll(stopovers);
                } else {
                    throw new RuntimeException("Illegal route designation : " + routeDesignation);
                }

                flights.add(scheduledFlight);
            }

            // TODO add support for multiple flights per flight id
            flightsById.put(flights.get(0).getAirlineFlightId(), flights);
            routeJourneys.put(routeDesignation, flightsById);
        }

        lineDataSet.setFlightRoutes(routes);
        lineDataSet.setRouteJourneys(routeJourneys);
        return lineDataSet;
    }

    public static LineDataSet createLineDataSet(String airlineIata, String lineDesignation, List<Pair<String, Integer>> routeJourneyPairs) {
        // init period
        LocalDate periodFromDate = LocalDate.now();
        LocalDate periodToDate = periodFromDate.plusMonths(1);

        List<Pair<String, List<LocalDate>>> routeJourneyPairsWithFixedDatesOfOperation =
                routeJourneyPairs.stream().map(p -> Pair.of(p.getKey(),
                        generateRandomDates(p.getValue(), periodFromDate, periodToDate))).collect(Collectors.toList());
        return createLineDataSetWithFixedDates(airlineIata, lineDesignation, routeJourneyPairsWithFixedDatesOfOperation, null);
    }

    private static String resolveLineName(String lineDesignation) {
        List<String> airportIatas = Splitter.on(DASH)
                .trimResults()
                .omitEmptyStrings()
                .limit(2)
                .splitToList(lineDesignation);
        return Joiner.on(DASH).skipNulls().join(airports.get(airportIatas.get(0)), airports.get(airportIatas.get(1)));
    }

    private static String resolveRouteName(String routeDesignation) {
        List<String> airportNames = Splitter.on(DASH)
                .trimResults()
                .omitEmptyStrings()
                .splitToList(routeDesignation).stream()
                .map(airports::get)
                .collect(Collectors.toList());
        return Joiner.on(DASH).skipNulls().join(airportNames);
    }

    private static List<String> resolveStops(String routeDesignation) {
        return Splitter.on(DASH)
                .trimResults()
                .omitEmptyStrings()
                .splitToList(routeDesignation).stream()
                .collect(Collectors.toList());
    }

    public static List<LocalDate> generateRandomDates(int cnt, LocalDate inclusive, LocalDate exclusive) {
        List<LocalDate> randomDates = new ArrayList<>();
        for (int i = 1; i <= cnt; i++) {
            randomDates.add(generateRandomDate(inclusive, exclusive));
        }
        return randomDates;
    }


    public static LocalDate generateRandomDate(LocalDate inclusive, LocalDate exclusive) {
        long days = ChronoUnit.DAYS.between(inclusive, exclusive);
        return inclusive.plusDays(new Random().nextInt((int) days + 1));
    }

    public static LocalTime generateOffsetTime() {
        int hour = generateRandomId(10, 20);
        int minute = generateRandomId(10, 50);
        return LocalTime.of(hour, minute);
    }

    public static int generateRandomId(int startInclusive, int endExclusive) {
        return RandomUtils.nextInt(startInclusive, endExclusive);
    }

}
