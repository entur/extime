package no.rutebanken.extime.converter;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import no.rutebanken.extime.model.*;
import no.rutebanken.extime.config.NetexStaticDataSet;
import no.rutebanken.extime.util.DateUtils;
import no.rutebanken.extime.util.ExtimeException;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static no.rutebanken.extime.Constants.DASH;
import static no.rutebanken.extime.model.AirportIATA.*;

@Component(value = "scheduledFlightConverter")
public class ScheduledFlightConverter {

    private static final Set<AirportIATA> LARGE_SIZED_AIRPORTS = EnumSet.of(OSL);
    private static final Set<AirportIATA> MEDIUM_SIZED_AIRPORTS = EnumSet.of(BGO, BOO, SVG, TRD);

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledFlightConverter.class);

    private final NetexStaticDataSet netexStaticDataSet;

    private final DateUtils dateUtils;

    private final FlightLegMapper flightLegMapper;

    private final Map<String, NetexStaticDataSet.StopPlaceDataSet> stopPlaceDataSets;

    public ScheduledFlightConverter(NetexStaticDataSet netexStaticDataSet, DateUtils dateUtils) {
        this.netexStaticDataSet = netexStaticDataSet;
        this.dateUtils = dateUtils;
        this.flightLegMapper = new FlightLegMapper();
        this.stopPlaceDataSets = netexStaticDataSet.getStopPlaces();
    }

    public List<LineDataSet> convertFlightEventsToLineCentricDataSets(List<FlightEvent> flightEvents) {
        List<FlightLeg> flightLegs = flightLegMapper.map(flightEvents);
        return convertFlightLegsToLineCentricDataSets(flightLegs);
    }

    List<LineDataSet> convertFlightLegsToLineCentricDataSets(List<FlightLeg> flightLegs) {
        Map<String, List<FlightLeg>> flightsByDepartureAirport = flightLegs.stream()
                .collect(Collectors.groupingBy(FlightLeg::getDepartureAirport));

        Set<Long> distinctFlightLegIds = Sets.newHashSet();
        List<ScheduledFlight> mergedScheduledFlights = Lists.newArrayList();

        for (FlightLeg flight : flightLegs) {
            List<FlightLeg> connectingFlightLegs = findConnectingFlightLegs(flight, flightsByDepartureAirport, distinctFlightLegIds);

            if (CollectionUtils.isEmpty(connectingFlightLegs)) {
                continue;
            }
            if (isMultiLegFlightRoute(connectingFlightLegs)) {
                List<Triple<StopVisitType, String, LocalTime>> stopovers = extractStopoversFromFlights(connectingFlightLegs);
                List<ScheduledStopover> scheduledStopovers = createScheduledStopovers(stopovers);
                ScheduledFlight scheduledFlightWithStopovers = convertToScheduledFlight(flight, scheduledStopovers);

                if (scheduledFlightWithStopovers != null) {
                    mergedScheduledFlights.add(scheduledFlightWithStopovers);
                }
            } else if (isDirectFlightRoute(connectingFlightLegs)) {
                ScheduledFlight directFlight = convertToScheduledFlight(flight, null);
                mergedScheduledFlights.add(directFlight);
            } else {
                throw new ExtimeException("Invalid flight " + flight.getId());
            }
        }

        LOGGER.info("Converted {} scheduled flights", mergedScheduledFlights.size());

        List<LineDataSet> lineDataSets = new ArrayList<>();

        // group by airline iata and unique lines
        Map<String, Map<String, List<ScheduledFlight>>> flightsByAirlineAndLine = mergedScheduledFlights.stream()
                .collect(Collectors.groupingBy(ScheduledFlight::getAirlineIATA,
                        Collectors.groupingBy(ScheduledFlight::getOperatingLine)));

        // find and merge all flights belonging to equivalent lines, and update map
        for (Map.Entry<String, Map<String, List<ScheduledFlight>>> entry : flightsByAirlineAndLine.entrySet()) {
            String airlineIata = entry.getKey();
            Map<String, List<ScheduledFlight>> flightsByLineDesignation = entry.getValue();
            Map<String, List<ScheduledFlight>> mergedFlightsByLineDesignation = findAndMergeFlightsByEquivalentLines(flightsByLineDesignation);

            for (Map.Entry<String, List<ScheduledFlight>> lineEntry : mergedFlightsByLineDesignation.entrySet()) {
                LineDataSet lineDataSet = populateFlightLineDataSet(airlineIata, lineEntry);
                lineDataSets.add(lineDataSet);
            }
        }

        return lineDataSets;
    }



    private LineDataSet populateFlightLineDataSet(String airlineIata, Map.Entry<String, List<ScheduledFlight>> flightsByLineEntry) {
        LineDataSet lineDataSet = new LineDataSet();
        lineDataSet.setAirlineIata(airlineIata);

        String lineDesignation = flightsByLineEntry.getKey();
        lineDataSet.setLineDesignation(lineDesignation);

        String lineName = getLineNameFromDesignation(lineDesignation);
        lineDataSet.setLineName(lineName);

        List<ScheduledFlight> flights = flightsByLineEntry.getValue();

        lineDataSet.setAvailabilityPeriod(dateUtils.generateAvailabilityPeriod());

        List<FlightRoute> flightRoutes = flights.stream()
                .map(flight -> new FlightRoute(flight.getRoutePattern(), getRouteNameFromDesignation(flight.getRoutePattern())))
                .filter(distinctByKey(FlightRoute::routeDesignation))
                .toList();
        lineDataSet.setFlightRoutes(flightRoutes);

        Map<String, Map<String, List<ScheduledFlight>>> journeysByRouteAndFlightId = flights.stream()
                .collect(Collectors.groupingBy(ScheduledFlight::getRoutePattern,
                        Collectors.groupingBy(ScheduledFlight::getAirlineFlightId)));

        lineDataSet.setRouteJourneys(journeysByRouteAndFlightId);

        return lineDataSet;
    }

    private String getLineNameFromDesignation(String lineDesignation) {
        List<String> airportIatas = Splitter.on(DASH)
                .trimResults()
                .omitEmptyStrings()
                .limit(2)
                .splitToList(lineDesignation);

        String firstAirportName = stopPlaceDataSets.get(airportIatas.get(0).toLowerCase()).getShortName();
        String secondAirportName = stopPlaceDataSets.get(airportIatas.get(1).toLowerCase()).getShortName();

        return Joiner.on(DASH).skipNulls().join(firstAirportName, secondAirportName);
    }

    private String getRouteNameFromDesignation(String routeDesignation) {
        List<String> airportNames = Splitter.on(DASH)
                .trimResults()
                .omitEmptyStrings()
                .splitToList(routeDesignation).stream()
                .map(String::toLowerCase)
                .map(iata -> stopPlaceDataSets.get(iata).getShortName())
                .toList();
        return Joiner.on(DASH).skipNulls().join(airportNames);
    }

    private Map<String, List<ScheduledFlight>> findAndMergeFlightsByEquivalentLines(Map<String, List<ScheduledFlight>> flightsByLineDesignation) {
        Set<String> lineDesignations = new HashSet<>();
        Map<String, List<ScheduledFlight>> mergedFlightsByLineDesignation = new HashMap<>();

        for (String lineDesignation : flightsByLineDesignation.keySet()) {
            if (!lineDesignations.contains(lineDesignation)) {
                List<ScheduledFlight> lineFlights = flightsByLineDesignation.get(lineDesignation);
                String oppositeLineDesignation = getOppositeLineDesignation(lineDesignation);
                String mostSignificantLineDesignation = findMostSignificantLineDesignation(lineDesignation);

                if (!lineDesignation.equals(oppositeLineDesignation)) {
                    if (flightsByLineDesignation.containsKey(oppositeLineDesignation)) {
                        List<ScheduledFlight> oppositeLineFlights = flightsByLineDesignation.get(oppositeLineDesignation);

                        List<ScheduledFlight> mergedFlights = Stream.of(lineFlights, oppositeLineFlights)
                                .flatMap(Collection::stream)
                                .toList();

                        mergedFlightsByLineDesignation.put(mostSignificantLineDesignation, mergedFlights);
                        lineDesignations.addAll(Arrays.asList(lineDesignation, oppositeLineDesignation));
                    } else {
                        mergedFlightsByLineDesignation.put(mostSignificantLineDesignation, lineFlights);
                        lineDesignations.add(lineDesignation);
                    }
                } else {
                    mergedFlightsByLineDesignation.put(mostSignificantLineDesignation, lineFlights);
                    lineDesignations.add(lineDesignation);
                }
            }
        }

        return mergedFlightsByLineDesignation;
    }

    private String getOppositeLineDesignation(String lineDesignation) {
        List<String> lineAirportIatas = Splitter.on(DASH)
                .trimResults()
                .omitEmptyStrings()
                .limit(2)
                .splitToList(lineDesignation);
        return Joiner.on(DASH).skipNulls().join(Lists.reverse(lineAirportIatas));
    }

    private String findMostSignificantLineDesignation(String lineDesignation) {
        List<AirportWithSize> airportsWithSize = new ArrayList<>(2);

        List<String> lineAirportIatas = Splitter.on(DASH)
                .trimResults()
                .omitEmptyStrings()
                .limit(2)
                .splitToList(lineDesignation);

        lineAirportIatas.forEach(iata -> {
            AirportWithSize airportWithSize = getAirportWithSize(iata);
            airportsWithSize.add(airportWithSize);
        });

        AirportIATA largestAirport = findLargestAirport(airportsWithSize);

        return largestAirport.name().equals(lineAirportIatas.getFirst()) ? lineDesignation :
                Joiner.on(DASH).skipNulls().join(Lists.reverse(lineAirportIatas));
    }

    private AirportWithSize getAirportWithSize(String airportIataCode) {
        boolean validEnum = EnumUtils.isValidEnum(AirportIATA.class, airportIataCode);

        if (validEnum) {
            AirportIATA airportIata = AirportIATA.valueOf(airportIataCode);
            if (LARGE_SIZED_AIRPORTS.contains(airportIata)) {
                return new AirportWithSize(airportIata, 3);
            } else if (MEDIUM_SIZED_AIRPORTS.contains(airportIata)) {
                return new AirportWithSize(airportIata, 2);
            } else {
                return new AirportWithSize(airportIata, 1);
            }
        } else {
            throw new ExtimeException("Invalid IATA code " + airportIataCode);
        }
    }

    private AirportIATA findLargestAirport(List<AirportWithSize> airportWithSizeList) {
        AirportWithSize airportWithSize1 = airportWithSizeList.get(0);
        AirportWithSize airportWithSize2 = airportWithSizeList.get(1);

        if (airportWithSize1.size() > airportWithSize2.size()) {
            return airportWithSize1.airportIata();
        } else if (airportWithSize1.size() < airportWithSize2.size()) {
            return airportWithSize2.airportIata();
        }
        return airportWithSize1.airportIata();
    }

    private <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = new HashSet<>();
        return t -> seen.add(keyExtractor.apply(t));
    }




    List<ScheduledStopover> createScheduledStopovers(List<Triple<StopVisitType, String, LocalTime>> stopovers) {
        List<ScheduledStopover> multiLegFlights = Lists.newArrayList();
        Triple<StopVisitType, String, LocalTime> tempArrivalStopover = null;

        for (ListIterator<Triple<StopVisitType, String, LocalTime>> it = stopovers.listIterator(); it.hasNext(); ) {
            Triple<StopVisitType, String, LocalTime> stopover = it.next();

            if (stopover.getLeft().equals(StopVisitType.DEPARTURE)) {
                ScheduledStopover scheduledStopover = new ScheduledStopover();
                scheduledStopover.setAirportIATA(stopover.getMiddle());
                scheduledStopover.setDepartureTime(stopover.getRight());

                if (tempArrivalStopover != null) {
                    scheduledStopover.setArrivalTime(tempArrivalStopover.getRight());
                }

                multiLegFlights.add(scheduledStopover);
            } else {
                if (!it.hasNext()) {
                    ScheduledStopover scheduledStopover = new ScheduledStopover();
                    scheduledStopover.setAirportIATA(stopover.getMiddle());
                    scheduledStopover.setArrivalTime(stopover.getRight());
                    multiLegFlights.add(scheduledStopover);
                } else {
                    tempArrivalStopover = stopover;
                }
            }
        }
        return multiLegFlights;
    }

    boolean isMultiLegFlightRoute(List<FlightLeg> flights) {
        return !CollectionUtils.isEmpty(flights) && flights.size() > 1;
    }

    boolean isDirectFlightRoute(List<FlightLeg> flights) {
        return !CollectionUtils.isEmpty(flights) && flights.size() == 1;
    }

    List<FlightLeg> findConnectingFlightLegs(FlightLeg currentFlightLeg, Map<String, List<FlightLeg>> flightsByDepartureAirportIata,
                                                    Set<Long> distinctFlightLegIds) {

        if (distinctFlightLegIds.contains(currentFlightLeg.getId())) {
            return Collections.emptyList();
        }

        List<FlightLeg> connectingFlightLegs = Lists.newArrayList(currentFlightLeg);

        connectingFlightLegs.addAll(findNextFlightLegs(currentFlightLeg, flightsByDepartureAirportIata, Lists.newArrayList()));

        if (connectingFlightLegs.size() > 1) {
            connectingFlightLegs.sort(Comparator.comparing(FlightLeg::getStd));
            connectingFlightLegs.forEach(flight -> distinctFlightLegIds.add(flight.getId()));
        }

        return connectingFlightLegs;
    }

    List<FlightLeg> findNextFlightLegs(FlightLeg currentFlightLeg, Map<String, List<FlightLeg>> flightsByDepartureAirportIata, List<FlightLeg> nextFlightLegs) {
        String arrivalAirportIata = currentFlightLeg.getArrivalAirport();
        List<FlightLeg> flightLegsForIata = flightsByDepartureAirportIata.get(arrivalAirportIata);

        if (flightLegsForIata != null) {
            Optional<FlightLeg> optionalFlightLeg = flightLegsForIata.stream().filter(f -> f.isNextLegOf(currentFlightLeg)).findFirst();
            optionalFlightLeg.ifPresent(flight -> {
                LOGGER.trace("Found next flight leg: {} for current flight leg {}", flight, currentFlightLeg);
                FlightLeg flightLeg = optionalFlightLeg.get();
                nextFlightLegs.add(flightLeg);
                findNextFlightLegs(flightLeg, flightsByDepartureAirportIata, nextFlightLegs);
            });
        }

        return nextFlightLegs;
    }

    List<Triple<StopVisitType, String, LocalTime>> extractStopoversFromFlights(List<FlightLeg> stopoverFlights) {
        List<Triple<StopVisitType, String, LocalTime>> stopoverTriples = new ArrayList<>();

        stopoverFlights.forEach(stopoverFlight -> {
            Triple<StopVisitType, String, LocalTime> departureTriple = new ImmutableTriple<>(
                    StopVisitType.DEPARTURE, stopoverFlight.getDepartureAirport(), dateUtils.toExportLocalTime(stopoverFlight.getStd()));

            Triple<StopVisitType, String, LocalTime> arrivalTriple = new ImmutableTriple<>(
                    StopVisitType.ARRIVAL, stopoverFlight.getArrivalAirport(), dateUtils.toExportLocalTime(stopoverFlight.getSta()));

            stopoverTriples.add(departureTriple);
            stopoverTriples.add(arrivalTriple);
        });

        if (stopoverTriples.isEmpty()) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(stopoverTriples);
        }
    }

    ScheduledFlight convertToScheduledFlight(FlightLeg flight, List<ScheduledStopover> scheduledStopovers) {
        ScheduledFlight scheduledFlight = new ScheduledFlight();
        scheduledFlight.setAirlineIATA(flight.getAirlineDesignator());
        scheduledFlight.setAirlineFlightId(flight.getFlightNumber());
        scheduledFlight.setDateOfOperation(dateUtils.toExportLocalDate(flight.getStd()));

        if (!CollectionUtils.isEmpty(scheduledStopovers)) {
            scheduledFlight.getScheduledStopovers().addAll(scheduledStopovers);
        } else {
            scheduledFlight.setDepartureAirportIATA(flight.getDepartureAirport());
            scheduledFlight.setArrivalAirportIATA(flight.getArrivalAirport());
            scheduledFlight.setTimeOfDeparture(dateUtils.toExportLocalTime(flight.getStd()));
            scheduledFlight.setTimeOfArrival(dateUtils.toExportLocalTime(flight.getSta()));
            }

        return scheduledFlight;
    }

    private record AirportWithSize(AirportIATA airportIata, int size) {
    }

    public boolean isKnownAirlineName(String airlineIata) {
        return netexStaticDataSet.getOrganisations().containsKey(airlineIata.toLowerCase());
    }

    public String getKnownAirlineName(String airlineIata) {
        return netexStaticDataSet.getOrganisations().get(airlineIata.toLowerCase()).getName();
    }

}
