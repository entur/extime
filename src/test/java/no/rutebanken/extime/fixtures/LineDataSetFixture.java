package no.rutebanken.extime.fixtures;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import no.rutebanken.extime.model.*;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

    private LineDataSetFixture() {}

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

    public static LineDataSet createLineDataSet(String airlineIata, String lineDesignation, List<Pair<String, Integer>> routeJourneyPairs) {
        LineDataSet lineDataSet = LineDataSetFixture.createBasicLineDataSet(airlineIata, lineDesignation);

        // init period
        LocalDate periodFromDate = LocalDate.now();
        LocalDate periodToDate = periodFromDate.plusMonths(1);

        OffsetTime offsetMidnight = OffsetTime.parse(OFFSET_MIDNIGHT_UTC).withOffsetSameLocal(ZoneOffset.UTC);
        OffsetDateTime requestPeriodFromDateTime = periodFromDate.atTime(offsetMidnight);
        OffsetDateTime requestPeriodToDateTime = periodToDate.atTime(offsetMidnight);

        AvailabilityPeriod availabilityPeriod = new AvailabilityPeriod(requestPeriodFromDateTime, requestPeriodToDateTime);
        lineDataSet.setAvailabilityPeriod(availabilityPeriod);

        // init routes and journeys
        List<FlightRoute> routes = new ArrayList<>();
        Map<String, Map<String, List<ScheduledFlight>>> routeJourneys = new HashMap<>();

        for (Pair<String, Integer> pair : routeJourneyPairs) {
            Map<String, List<ScheduledFlight>> flightsById = new HashMap<>();
            List<ScheduledFlight> flights = new ArrayList<>();

            String routeDesignation = pair.getKey();
            routes.add(new FlightRoute(routeDesignation, resolveRouteName(routeDesignation)));

            for (int i = 1; i <= pair.getValue(); i++) {
                ScheduledFlight scheduledFlight = new ScheduledFlight();
                scheduledFlight.setAirlineIATA(airlineIata);

                String airlineFlightId = airlineIata + generateRandomId(100, 9999);
                scheduledFlight.setAirlineFlightId(airlineFlightId);
                scheduledFlight.setDateOfOperation(generateRandomDate(periodFromDate, periodToDate));

                List<String> routeStops = resolveStops(routeDesignation);
                if (routeStops.size() == 2) {
                    String departureAirportIata = routeStops.get(0);
                    scheduledFlight.setDepartureAirportIATA(departureAirportIata);
                    scheduledFlight.setDepartureAirportName(airports.get(departureAirportIata));

                    String arrivalAirportIata = routeStops.get(1);
                    scheduledFlight.setArrivalAirportIATA(arrivalAirportIata);
                    scheduledFlight.setArrivalAirportName(airports.get(arrivalAirportIata));

                    OffsetTime timeOfDeparture = generateOffsetTime();
                    scheduledFlight.setTimeOfDeparture(timeOfDeparture);

                    OffsetTime timeOfArrival = timeOfDeparture.plusHours(1);
                    scheduledFlight.setTimeOfArrival(timeOfArrival);
                } else if (routeStops.size() > 2) {
                    List<ScheduledStopover> stopovers = Lists.newArrayList();
                    OffsetTime timeOfArrival = generateOffsetTime();
                    OffsetTime timeOfDeparture;

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

/*
    public ScheduledFlight convertToScheduledFlight(Flight flight, List<ScheduledStopover> scheduledStopovers) {
        Joiner joiner = Joiner.on(DASH).skipNulls();

        ScheduledFlight scheduledFlight = new ScheduledFlight();
        scheduledFlight.setAirlineIATA(flight.getAirlineDesignator());
        scheduledFlight.setAirlineFlightId(flight.getAirlineDesignator() + flight.getFlightNumber());
        scheduledFlight.setDateOfOperation(flight.getDateOfOperation());

        if (CollectionUtils.isNotEmpty(scheduledStopovers)) {
            scheduledFlight.getScheduledStopovers().addAll(scheduledStopovers);
            String departureAirportIata = scheduledStopovers.get(0).getAirportIATA();
            String arrivalAirportIata = scheduledStopovers.get(scheduledStopovers.size() - 1).getAirportIATA();
            String lineDesignation = joiner.join(departureAirportIata, arrivalAirportIata);
            scheduledFlight.setLineDesignation(lineDesignation);

            List<String> airportIatas = scheduledStopovers.stream()
                    .map(ScheduledStopover::getAirportIATA)
                    .collect(Collectors.toList());
            scheduledFlight.setStopsDesignation(joiner.join(airportIatas));
        } else {
            scheduledFlight.setFlightId(flight.getId());
            scheduledFlight.setDepartureAirportIATA(flight.getDepartureStation());
            scheduledFlight.setArrivalAirportIATA(flight.getArrivalStation());
            scheduledFlight.setTimeOfDeparture(flight.getStd());
            scheduledFlight.setTimeOfArrival(flight.getSta());
            String lineDesignation = joiner.join(scheduledFlight.getDepartureAirportIATA(), scheduledFlight.getArrivalAirportIATA());
            scheduledFlight.setLineDesignation(lineDesignation);
            scheduledFlight.setStopsDesignation(lineDesignation);
        }

        //scheduledFlight.setTimesDesignation("");

        return scheduledFlight;
    }
*/


    public static LineDataSet createLineDataSetWithStopovers(String airlineIata, String lineDesignation, List<String> routeDesignations) {
        LineDataSet lineDataSet = LineDataSetFixture.createBasicLineDataSet(airlineIata, lineDesignation);

        // init period
        String firstDateOfOperation = "2017-01-30";

        LocalDate requestPeriodFromDate = LocalDate.parse(firstDateOfOperation);
        LocalDate requestPeriodToDate = requestPeriodFromDate.plusDays(1);

        OffsetTime offsetMidnight = OffsetTime.parse(OFFSET_MIDNIGHT_UTC).withOffsetSameLocal(ZoneOffset.UTC);
        OffsetDateTime requestPeriodFromDateTime = requestPeriodFromDate.atTime(offsetMidnight);
        OffsetDateTime requestPeriodToDateTime = requestPeriodToDate.atTime(offsetMidnight);

        AvailabilityPeriod availabilityPeriod = new AvailabilityPeriod(requestPeriodFromDateTime, requestPeriodToDateTime);
        lineDataSet.setAvailabilityPeriod(availabilityPeriod);

        // init routes
        List<FlightRoute> routes = new ArrayList<>();
        routeDesignations.forEach(designation -> routes.add(new FlightRoute(designation, resolveRouteName(designation))));
        lineDataSet.setFlightRoutes(routes);

        // init journeys
        Map<String, Map<String, List<ScheduledFlight>>> routeJourneys = new HashMap<>();

        List<ScheduledStopover> flight1Stopovers = Lists.newArrayList(
                createScheduledStopover("OSL", null, OffsetTime.parse("07:10:00Z")),
                createScheduledStopover("SOG", OffsetTime.parse("07:50:00Z"), OffsetTime.parse("08:10:00Z")),
                createScheduledStopover("BGO", OffsetTime.parse("09:00:00Z"), null)
        );
        List<ScheduledFlight> routeOslSogBgoFlights = Collections.singletonList(
                createScheduledStopoverFlight(airlineIata, "DY602", "2017-01-30", flight1Stopovers));

        List<ScheduledStopover> flight2Stopovers = Lists.newArrayList(
                createScheduledStopover("BGO", null, OffsetTime.parse("07:10:00Z")),
                createScheduledStopover("SOG", OffsetTime.parse("07:50:00Z"), OffsetTime.parse("08:10:00Z")),
                createScheduledStopover("OSL", OffsetTime.parse("09:00:00Z"), null)
        );
        List<ScheduledFlight> routeBgoSogOslFlights = Collections.singletonList(
                createScheduledStopoverFlight(airlineIata, "DY633", "2017-01-31", flight2Stopovers));

        Map<String, List<ScheduledFlight>> dy602Flights = new HashMap<>();
        dy602Flights.put("DY602", routeOslSogBgoFlights);
        routeJourneys.put("OSL-SOG-BGO", dy602Flights);

        Map<String, List<ScheduledFlight>> dy633Flights = new HashMap<>();
        dy633Flights.put("DY633", routeBgoSogOslFlights);
        routeJourneys.put("BGO-SOG-OSL", dy633Flights);

        lineDataSet.setRouteJourneys(routeJourneys);
        return lineDataSet;
    }

    private static ScheduledStopover createScheduledStopover(String airportIata, OffsetTime arrivalTime, OffsetTime departureTime) {
        ScheduledStopover scheduledStopover = new ScheduledStopover();
        scheduledStopover.setAirportIATA(airportIata);
        if (arrivalTime != null) {
            scheduledStopover.setArrivalTime(arrivalTime);
        }
        if (departureTime != null) {
            scheduledStopover.setDepartureTime(departureTime);
        }
        return scheduledStopover;
    }

    public static AvailabilityPeriod createAvailabilityPeriod(LocalDate periodFromDate, LocalDate periodToDate) {
        OffsetTime offsetMidnight = OffsetTime.parse(OFFSET_MIDNIGHT_UTC).withOffsetSameLocal(ZoneOffset.UTC);
        OffsetDateTime requestPeriodFromDateTime = periodFromDate.atTime(offsetMidnight);
        OffsetDateTime requestPeriodToDateTime = periodToDate.atTime(offsetMidnight);
        return new AvailabilityPeriod(requestPeriodFromDateTime, requestPeriodToDateTime);
    }

    private static ScheduledFlight createScheduledStopoverFlight(String airlineIata, String airlineFlightId, String dateOfOperation, List<ScheduledStopover> stopovers) {
        ScheduledFlight scheduledFlight = new ScheduledFlight();
        scheduledFlight.setAirlineIATA(airlineIata);
        scheduledFlight.setAirlineName(airlines.get(airlineIata));
        scheduledFlight.setAirlineFlightId(airlineFlightId);
        scheduledFlight.setDateOfOperation(LocalDate.parse(dateOfOperation));
        scheduledFlight.setScheduledStopovers(stopovers);
        return scheduledFlight;
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

    public static LocalDate generateRandomDate(LocalDate inclusive, LocalDate exclusive) {
        long days = ChronoUnit.DAYS.between(inclusive, exclusive);
        return inclusive.plusDays(new Random().nextInt((int) days + 1));
    }

    public static OffsetTime generateOffsetTime() {
        int hour = generateRandomId(10, 20);
        int minute = generateRandomId(10, 50);
        return OffsetTime.parse(Joiner.on(COLON).join(hour, minute, "00Z"));
    }

    public static int generateRandomId(int startInclusive, int endExclusive) {
        return RandomUtils.nextInt(startInclusive, endExclusive);
    }

}
