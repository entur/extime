package no.rutebanken.extime.converter;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import no.rutebanken.extime.config.NetexStaticDataSet;
import no.rutebanken.extime.model.AirlineDesignator;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.FlightPredicate;
import no.rutebanken.extime.model.FlightRoute;
import no.rutebanken.extime.model.LineDataSet;
import no.rutebanken.extime.model.ScheduledFlight;
import no.rutebanken.extime.model.ScheduledStopover;
import no.rutebanken.extime.model.StopVisitType;
import no.rutebanken.extime.util.AvinorTimetableUtils;
import no.rutebanken.extime.util.DateUtils;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TypeConverter;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static no.rutebanken.extime.Constants.DASH;
import static no.rutebanken.extime.Constants.DEFAULT_DATE_TIME_FORMATTER;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_HTTP_URI;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_URI_PARAMETERS;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.PROPERTY_OFFLINE_MODE;

@Component(value = "scheduledFlightConverter")
public class ScheduledFlightConverter {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledFlightConverter.class);

    @Autowired
    private NetexStaticDataSet netexStaticDataSet;

    @Autowired
    private DateUtils dateUtils;

    @Autowired
    private TypeConverter typeConverter;

    @Autowired
    private ProducerTemplate producerTemplate;

    @EndpointInject(uri = "direct:fetchXmlStreamFromHttpFeed")
    private Endpoint feedEndpoint;

    @Value("${avinor.timetable.feed.endpoint}")
    private String httpFeedEndpointUri;

    @Value("${avinor.timetable.period.months}")
    private int numberOfMonthsInPeriod;

    private String uriParametersFormat = "airport=%s&direction=%s&designator=%s&number=%s&PeriodFrom=%sZ&PeriodTo=%sZ";

    private Map<String, NetexStaticDataSet.StopPlaceDataSet> stopPlaceDataSets;

    @PostConstruct
    public void init() {
        stopPlaceDataSets = netexStaticDataSet.getStopPlaces();
    }

    public List<LineDataSet> convertToLineCentricDataSets(@ExchangeProperty(PROPERTY_OFFLINE_MODE) Boolean offlineMode, List<Flight> scheduledFlights) {

        // TODO check for codeshare flights here, use the infrequent designator enumset group, to only check international airlines

        List<Flight> filteredFlights;
        if (Boolean.TRUE.equals(offlineMode)) {
            //filteredFlights = scheduledFlights;
            filteredFlights = filterFlightsWithInfrequentDesignator(scheduledFlights); // TODO temp fix removing flights with infrequent airline designators
        } else {
            //filteredFlights = filterValidFlights(scheduledFlights);
            filteredFlights = filterFlightsWithInfrequentDesignator(scheduledFlights); // TODO temp fix removing flights with infrequent airline designators
        }

        filteredFlights = filterFlightsWithArrivalAtDepartureStation(filteredFlights);

        Map<String, List<Flight>> flightsByDepartureAirport = filteredFlights.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));

        Map<String, List<Flight>> flightsByArrivalAirportIata = filteredFlights.stream()
                .collect(Collectors.groupingBy(Flight::getArrivalStation));

        Set<BigInteger> distinctFlightLegIds = Sets.newHashSet();
        List<ScheduledFlight> mergedScheduledFlights = Lists.newArrayList();

        for (Flight flight : filteredFlights) {
            List<Flight> connectingFlightLegs = findConnectingFlightLegs(flight, flightsByDepartureAirport, flightsByArrivalAirportIata, distinctFlightLegIds);

            if (CollectionUtils.isEmpty(connectingFlightLegs)) {
                continue;
            }
            if (isMultiLegFlightRoute(connectingFlightLegs)) {
                List<Triple<StopVisitType, String, LocalTime>> stopovers = extractStopoversFromFlights(connectingFlightLegs);
                List<ScheduledStopover> scheduledStopovers = createScheduledStopovers(stopovers, flight.getDateOfOperation());
                ScheduledFlight scheduledFlightWithStopovers = convertToScheduledFlight(flight, scheduledStopovers);

                if (scheduledFlightWithStopovers != null) {
                    mergedScheduledFlights.add(scheduledFlightWithStopovers);
                }
            } else if (isDirectFlightRoute(connectingFlightLegs)) {
                ScheduledFlight directFlight = convertToScheduledFlight(flight, null);
                mergedScheduledFlights.add(directFlight);
            } else {
                logger.error("Flight with unique id: {}, and flightId: {}-{} is NOT a valid flight",
                        flight.getId(),
                        flight.getAirlineDesignator(),
                        flight.getFlightNumber());
                throw new RuntimeException("Invalid flight");
            }
        }

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

    /**
     * Remove any flights where departure station and arrival station are the same. Causes loops in conversion and are probably not relevant (if event correct).
     */
    private List<Flight> filterFlightsWithArrivalAtDepartureStation(List<Flight> filteredFlights) {
        return filteredFlights.stream().filter(flight -> !Objects.equals(flight.getDepartureStation(), flight.getArrivalStation())).collect(Collectors.toList());
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
                .filter(distinctByKey(FlightRoute::getRouteDesignation))
                .collect(Collectors.toList());
        lineDataSet.setFlightRoutes(flightRoutes);

        Map<String, Map<String, List<ScheduledFlight>>> journeysByRouteAndFlightId = flights.stream()
                .collect(Collectors.groupingBy(ScheduledFlight::getRoutePattern,
                        Collectors.groupingBy(ScheduledFlight::getAirlineFlightId)));

        // TODO consider sorting all internally grouped flights by operating day
        // .sorted(Comparator.comparing(ScheduledFlight::getDateOfOperation))

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
                .collect(Collectors.toList());
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
                                .collect(Collectors.toList());

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

        return largestAirport.name().equals(lineAirportIatas.get(0)) ? lineDesignation :
                Joiner.on(DASH).skipNulls().join(Lists.reverse(lineAirportIatas));
    }

    private AirportWithSize getAirportWithSize(String airportIataCode) {
        boolean validEnum = EnumUtils.isValidEnum(AirportIATA.class, airportIataCode);

        if (validEnum) {
            AirportIATA airportIata = AirportIATA.valueOf(airportIataCode);
            if (AirportIATA.LARGE_SIZED_AIRPORTS.contains(airportIata)) {
                return new AirportWithSize(airportIata, 3);
            } else if (AirportIATA.MEDIUM_SIZED_AIRPORTS.contains(airportIata)) {
                return new AirportWithSize(airportIata, 2);
            } else {
                return new AirportWithSize(airportIata, 1);
            }
        } else {
            logger.error("Invalid iata code : {}", airportIataCode);
            throw new RuntimeException("Invalid iata code");
        }
    }

    private AirportIATA findLargestAirport(List<AirportWithSize> airportWithSizeList) {
        AirportWithSize airportWithSize1 = airportWithSizeList.get(0);
        AirportWithSize airportWithSize2 = airportWithSizeList.get(1);

        if (airportWithSize1.getSize() > airportWithSize2.getSize()) {
            return airportWithSize1.getAirportIata();
        } else if (airportWithSize1.getSize() < airportWithSize2.getSize()) {
            return airportWithSize2.getAirportIata();
        }
        return airportWithSize1.getAirportIata();
    }

    private <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = new HashSet<>();
        return t -> seen.add(keyExtractor.apply(t));
    }

    private List<Flight> filterFlightsWithInfrequentDesignator(List<Flight> scheduledFlights) {
        Set<BigInteger> invalidFlightIds = Sets.newHashSet();

        for (Flight flight : scheduledFlights) {
            if (hasInfrequentDesignator(flight)) {
                invalidFlightIds.add(flight.getId());
            }
        }

        return scheduledFlights.stream()
                .filter(flight -> !invalidFlightIds.contains(flight.getId()))
                .collect(Collectors.toList());
    }

    private List<Flight> filterValidFlights(List<Flight> scheduledFlights) {
        Set<BigInteger> invalidFlightIds = Sets.newHashSet();

        for (Flight flight : scheduledFlights) {
            if (hasInfrequentDesignator(flight)) {

                if (isInternationalDepartureFound(flight) || isInternationalArrivalFound(flight)) {
                    logger.info("Flight with id {} is part of an international flight route, and marked as invalid", flight.getId());
                    invalidFlightIds.add(flight.getId());
                }
            }
        }

        logger.info("Number of invalid flights : {}", invalidFlightIds.size());

        return scheduledFlights.stream()
                .filter(flight -> !invalidFlightIds.contains(flight.getId()))
                .collect(Collectors.toList());
    }

    private boolean hasInfrequentDesignator(Flight flight) {
        boolean validEnum = EnumUtils.isValidEnum(AirlineDesignator.class, flight.getAirlineDesignator());

        if (validEnum) {
            AirlineDesignator designator = AirlineDesignator.valueOf(flight.getAirlineDesignator());
            return !AirlineDesignator.commonDesignators.contains(designator);
        }

        return true;
    }

    private boolean isInternationalDepartureFound(Flight currentFlight) {
        String dateOfOperation = currentFlight.getDateOfOperation().format(DEFAULT_DATE_TIME_FORMATTER);

        String uriParameters = String.format(uriParametersFormat,
                currentFlight.getDepartureStation(), StopVisitType.ARRIVAL.getCode(),
                currentFlight.getAirlineDesignator(), currentFlight.getFlightNumber(), dateOfOperation, dateOfOperation);

        Flight previousFlight = fetchConnectingFlight(uriParameters);

        return previousFlight != null && isValidPreviousFlight(previousFlight, currentFlight) &&
                (!AvinorTimetableUtils.isDomesticFlight(previousFlight) || isInternationalDepartureFound(previousFlight));
    }

    private boolean isInternationalArrivalFound(Flight currentFlight) {
        String dateOfOperation = currentFlight.getDateOfOperation().format(DEFAULT_DATE_TIME_FORMATTER);

        String uriParameters = String.format(uriParametersFormat,
                currentFlight.getDepartureStation(), StopVisitType.DEPARTURE.getCode(),
                currentFlight.getAirlineDesignator(), currentFlight.getFlightNumber(), dateOfOperation, dateOfOperation);

        Flight nextFlight = fetchConnectingFlight(uriParameters);

        return nextFlight != null && isValidNextFlight(nextFlight, currentFlight) &&
                (!AvinorTimetableUtils.isDomesticFlight(nextFlight) || isInternationalArrivalFound(nextFlight));
    }

    private Flight fetchConnectingFlight(String uriParameters) {
        Map<String, Object> headers = Maps.newHashMap();
        headers.put(HEADER_EXTIME_HTTP_URI, httpFeedEndpointUri);
        headers.put(HEADER_EXTIME_URI_PARAMETERS, uriParameters);

        InputStream xmlStream = producerTemplate.requestBodyAndHeaders(feedEndpoint, null, headers, InputStream.class);
        Flights flights = typeConverter.convertTo(Flights.class, xmlStream);
        return !flights.getFlight().isEmpty() ? flights.getFlight().get(0) : null;
    }

    private boolean isValidPreviousFlight(Flight previousFlight, Flight currentFlight) {
        return previousFlight.getSta().isBefore(currentFlight.getStd());
    }

    private boolean isValidNextFlight(Flight nextFlight, Flight currentFlight) {
        return nextFlight.getStd().isAfter(currentFlight.getSta());
    }

    public List<ScheduledStopover> createScheduledStopovers(List<Triple<StopVisitType, String, LocalTime>> stopovers, ZonedDateTime dateOfOperation) {
        List<ScheduledStopover> multiLegFlights = Lists.newArrayList();
        Triple<StopVisitType, String, LocalTime> tempArrivalStopover = null;

        for (ListIterator<Triple<StopVisitType, String, LocalTime>> it = stopovers.listIterator(); it.hasNext(); ) {
            Triple<StopVisitType, String, LocalTime> stopover = it.next();

            if (stopover.getLeft().equals(StopVisitType.DEPARTURE)) {
                ScheduledStopover scheduledStopover = new ScheduledStopover();
                scheduledStopover.setAirportIATA(stopover.getMiddle());
                scheduledStopover.setDepartureTime(dateUtils.toExportLocalTime(dateOfOperation.with(stopover.getRight())));

                if (tempArrivalStopover != null) {
                    scheduledStopover.setArrivalTime(dateUtils.toExportLocalTime(dateOfOperation.with(tempArrivalStopover.getRight())));
                }

                multiLegFlights.add(scheduledStopover);
            } else {
                if (!it.hasNext()) {
                    ScheduledStopover scheduledStopover = new ScheduledStopover();
                    scheduledStopover.setAirportIATA(stopover.getMiddle());
                    scheduledStopover.setArrivalTime(dateUtils.toExportLocalTime(dateOfOperation.with(stopover.getRight())));
                    multiLegFlights.add(scheduledStopover);
                } else {
                    tempArrivalStopover = stopover;
                }
            }
        }
        return multiLegFlights;
    }

    public boolean isMultiLegFlightRoute(List<Flight> flights) {
        return !CollectionUtils.isEmpty(flights) && flights.size() > 1;
    }

    public boolean isDirectFlightRoute(List<Flight> flights) {
        return !CollectionUtils.isEmpty(flights) && flights.size() == 1;
    }

    public List<Flight> findConnectingFlightLegs(Flight currentFlightLeg, Map<String, List<Flight>> flightsByDepartureAirportIata,
                                                 Map<String, List<Flight>> flightsByArrivalAirportIata, Set<BigInteger> distinctFlightLegIds) {

        if (distinctFlightLegIds.contains(currentFlightLeg.getId())) {
            return Collections.emptyList();
        }

        List<Flight> connectingFlightLegs = Lists.newArrayList(currentFlightLeg);
        List<Flight> previousFlightLegs = findPreviousFlightLegs(currentFlightLeg, flightsByArrivalAirportIata, Lists.newArrayList());

        if (!CollectionUtils.isEmpty(previousFlightLegs)) {
            connectingFlightLegs.addAll(previousFlightLegs);
        }

        List<Flight> nextFlightLegs = findNextFlightLegs(currentFlightLeg, flightsByDepartureAirportIata, Lists.newArrayList());

        if (!CollectionUtils.isEmpty(nextFlightLegs)) {
            connectingFlightLegs.addAll(nextFlightLegs);
        }

        if (connectingFlightLegs.size() > 1) {
            connectingFlightLegs.sort(Comparator.comparing(Flight::getStd));
            connectingFlightLegs.forEach(flight -> distinctFlightLegIds.add(flight.getId()));
        }

        return connectingFlightLegs;
    }

    public List<Flight> findPreviousFlightLegs(Flight currentFlightLeg, Map<String, List<Flight>> flightsByArrivalAirportIata, List<Flight> previousFlightLegs) {
        String departureAirportIata = currentFlightLeg.getDepartureStation();
        List<Flight> flightLegsForIata = flightsByArrivalAirportIata.get(departureAirportIata);
        Predicate<Flight> flightPredicate = FlightPredicate.matchPreviousFlight(currentFlightLeg);

        logger.trace("Attempting to find previous flight legs for flight leg: {} ", currentFlightLeg);

        if (flightLegsForIata != null) {
            Optional<Flight> optionalFlightLeg = findOptionalConnectingFlightLeg(flightPredicate, flightLegsForIata);
            optionalFlightLeg.ifPresent(flight -> {
                Flight flightLeg = optionalFlightLeg.get();
                previousFlightLegs.add(flightLeg);
                findPreviousFlightLegs(flightLeg, flightsByArrivalAirportIata, previousFlightLegs);
            });
        }

        return previousFlightLegs;
    }

    public List<Flight> findNextFlightLegs(Flight currentFlightLeg, Map<String, List<Flight>> flightsByDepartureAirportIata, List<Flight> nextFlightLegs) {
        String arrivalAirportIata = currentFlightLeg.getArrivalStation();
        List<Flight> flightLegsForIata = flightsByDepartureAirportIata.get(arrivalAirportIata);
        Predicate<Flight> flightPredicate = FlightPredicate.matchNextFlight(currentFlightLeg);

        if (flightLegsForIata != null) {
            Optional<Flight> optionalFlightLeg = findOptionalConnectingFlightLeg(flightPredicate, flightLegsForIata);
            optionalFlightLeg.ifPresent(flight -> {
                Flight flightLeg = optionalFlightLeg.get();
                nextFlightLegs.add(flightLeg);
                findNextFlightLegs(flightLeg, flightsByDepartureAirportIata, nextFlightLegs);
            });
        }

        return nextFlightLegs;
    }

    public Optional<Flight> findOptionalConnectingFlightLeg(Predicate<Flight> flightPredicate, List<Flight> flightLegs) {
        return flightLegs.stream()
                .filter(flightPredicate)
                .findFirst();
    }

    public List<Triple<StopVisitType, String, LocalTime>> extractStopoversFromFlights(List<Flight> stopoverFlights) {
        List<Triple<StopVisitType, String, LocalTime>> stopoverTriples = new ArrayList<>();

        stopoverFlights.forEach(stopoverFlight -> {
            Triple<StopVisitType, String, LocalTime> departureTriple = new ImmutableTriple<>(
                    StopVisitType.DEPARTURE, stopoverFlight.getDepartureStation(), stopoverFlight.getStd());

            Triple<StopVisitType, String, LocalTime> arrivalTriple = new ImmutableTriple<>(
                    StopVisitType.ARRIVAL, stopoverFlight.getArrivalStation(), stopoverFlight.getSta());

            stopoverTriples.add(departureTriple);
            stopoverTriples.add(arrivalTriple);
        });

        if (stopoverTriples.isEmpty()) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(stopoverTriples);
        }
    }

    public ScheduledFlight convertToScheduledFlight(Flight flight, List<ScheduledStopover> scheduledStopovers) {
        Joiner joiner = Joiner.on(DASH).skipNulls();

        ScheduledFlight scheduledFlight = new ScheduledFlight();
        scheduledFlight.setAirlineIATA(flight.getAirlineDesignator());
        scheduledFlight.setAirlineFlightId(flight.getAirlineDesignator() + flight.getFlightNumber());
        scheduledFlight.setDateOfOperation(dateUtils.toExportLocalDateTime(flight.getDateOfOperation()).toLocalDate());

        if (!CollectionUtils.isEmpty(scheduledStopovers)) {
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
            scheduledFlight.setTimeOfDeparture(dateUtils.toExportLocalTime(flight.getDateOfOperation().with(flight.getStd())));
            scheduledFlight.setTimeOfArrival(dateUtils.toExportLocalTime(flight.getDateOfOperation().with(flight.getSta())));
            String lineDesignation = joiner.join(scheduledFlight.getDepartureAirportIATA(), scheduledFlight.getArrivalAirportIATA());
            scheduledFlight.setLineDesignation(lineDesignation);
            scheduledFlight.setStopsDesignation(lineDesignation);
        }

        //scheduledFlight.setTimesDesignation("");

        return scheduledFlight;
    }

    private class AirportWithSize {
        AirportIATA airportIata;
        int size;

        public AirportWithSize(AirportIATA airportIata, int size) {
            this.airportIata = airportIata;
            this.size = size;
        }

        public AirportIATA getAirportIata() {
            return airportIata;
        }

        public void setAirportIata(AirportIATA airportIata) {
            this.airportIata = airportIata;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }

    public boolean isKnownAirlineName(String airlineIata) {
        return netexStaticDataSet.getOrganisations().containsKey(airlineIata.toLowerCase());
    }

    public String getKnownAirlineName(String airlineIata) {
        return netexStaticDataSet.getOrganisations().get(airlineIata.toLowerCase()).getName();
    }

}
