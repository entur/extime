package no.rutebanken.extime.converter;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import no.rutebanken.extime.config.NetexStaticDataSet;
import no.rutebanken.extime.config.NetexStaticDataSet.StopPlaceDataSet;
import no.rutebanken.extime.model.*;
import no.rutebanken.extime.util.AvinorTimetableUtils;
import org.apache.camel.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static no.rutebanken.extime.Constants.*;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_HTTP_URI;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_URI_PARAMETERS;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.PROPERTY_OFFLINE_MODE;

@Component(value = "scheduledFlightConverter")
public class ScheduledFlightConverter {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledFlightConverter.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_PATTERN);

    @Autowired
    private NetexStaticDataSet netexStaticDataSet;

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

    private Map<String, StopPlaceDataSet> stopPlaceDataSets;

    @PostConstruct
    public void init() {
        stopPlaceDataSets = netexStaticDataSet.getStopPlaces();
    }

    public List<LineDataSet> convertToLineCentricDataSets(@ExchangeProperty(PROPERTY_OFFLINE_MODE) Boolean offlineMode, List<Flight> scheduledFlights) {

        // TODO For now we create a separate from- and to date in converter, but this should actually
        // TODO come as input headers from previous date range generator, to be 100% sure these are always the same
        LocalDate requestPeriodFromDate = LocalDate.now(ZoneId.of(DEFAULT_ZONE_ID));
        LocalDate requestPeriodToDate = requestPeriodFromDate.plusMonths(numberOfMonthsInPeriod);

        OffsetTime offsetMidnight = OffsetTime.parse(OFFSET_MIDNIGHT_UTC).withOffsetSameLocal(ZoneOffset.UTC);
        OffsetDateTime requestPeriodFromDateTime = requestPeriodFromDate.atTime(offsetMidnight);
        OffsetDateTime requestPeriodToDateTime = requestPeriodToDate.atTime(offsetMidnight);

        // TODO check for codeshare flights here, use the infrequent designator enumset group, to only check international airlines

        List<Flight> filteredFlights;
        if (offlineMode) {
            filteredFlights = scheduledFlights;
        } else {
            filteredFlights = filterValidFlights(scheduledFlights);
        }

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
                List<Triple<StopVisitType, String, OffsetTime>> stopovers = extractStopoversFromFlights(connectingFlightLegs);
                List<ScheduledStopover> scheduledStopovers = createScheduledStopovers(stopovers);
                ScheduledFlight scheduledFlightWithStopovers = createScheduledFlightWithStopovers(flight, scheduledStopovers);

                if (scheduledFlightWithStopovers != null) {
                    mergedScheduledFlights.add(scheduledFlightWithStopovers);
                }
            } else if (isDirectFlightRoute(connectingFlightLegs)) {
                ScheduledFlight directFlight = convertToScheduledFlight(flight, null);
                mergedScheduledFlights.add(directFlight);
            } else {
                logger.error("Flight with unique id: {}, and flightId: {} is NOT a valid flight",
                        flight.getId(),
                        flight.getAirlineDesignator(),
                        flight.getFlightNumber());
                throw new RuntimeException("Invalid flight");
            }
        }

        List<LineDataSet> lineDataSets = new ArrayList<>();

        // TODO split up the following grouping which does not group correctly
        for (ScheduledFlight scheduledFlight : mergedScheduledFlights) {
            System.out.println(scheduledFlight);
        }

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
                LineDataSet lineDataSet = populateFlightLineDataSet(requestPeriodFromDateTime, requestPeriodToDateTime, airlineIata, lineEntry);
                lineDataSets.add(lineDataSet);
            }
        }

        return lineDataSets;
    }

    private LineDataSet populateFlightLineDataSet(OffsetDateTime requestPeriodFromDateTime,
                                                  OffsetDateTime requestPeriodToDateTime,
                                                  String airlineIata,
                                                  Map.Entry<String, List<ScheduledFlight>> flightsByLineEntry) {

        LineDataSet lineDataSet = new LineDataSet();
        lineDataSet.setAirlineIata(airlineIata);

        String lineDesignation = flightsByLineEntry.getKey();
        lineDataSet.setLineDesignation(lineDesignation);

        String lineName = getLineNameFromDesignation(lineDesignation);
        lineDataSet.setLineName(lineName);

        List<ScheduledFlight> flights = flightsByLineEntry.getValue();

        AvailabilityPeriod availabilityPeriod = new AvailabilityPeriod(requestPeriodFromDateTime, requestPeriodToDateTime);
        lineDataSet.setAvailabilityPeriod(availabilityPeriod);

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
            } else if (AirportIATA.MEDIUM_SIZED_AIRPORTS.contains(airportIata))  {
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

    // TODO consider supporting parallell mapping
/*
    private <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> map = new ConcurrentHashMap<>();
        return t -> map.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }
*/

    private <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = new HashSet<>();
        return t -> seen.add(keyExtractor.apply(t));
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
        String dateOfOperation = currentFlight.getDateOfOperation().format(DATE_TIME_FORMATTER);

        String uriParameters = String.format(uriParametersFormat,
                currentFlight.getDepartureStation(), StopVisitType.ARRIVAL.getCode(),
                currentFlight.getAirlineDesignator(), currentFlight.getFlightNumber(), dateOfOperation, dateOfOperation);

        Flight previousFlight = fetchConnectingFlight(uriParameters);

        return previousFlight != null && isValidPreviousFlight(previousFlight, currentFlight) &&
                (!AvinorTimetableUtils.isDomesticFlight(previousFlight) || isInternationalDepartureFound(previousFlight));
    }

    private boolean isInternationalArrivalFound(Flight currentFlight) {
        String dateOfOperation = currentFlight.getDateOfOperation().format(DATE_TIME_FORMATTER);

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

    public List<ScheduledStopover> createScheduledStopovers(List<Triple<StopVisitType, String, OffsetTime>> stopovers) {
        List<ScheduledStopover> multiLegFlights = Lists.newArrayList();
        Triple<StopVisitType, String, OffsetTime> tempArrivalStopover = null;

        for (ListIterator<Triple<StopVisitType, String, OffsetTime>> it = stopovers.listIterator(); it.hasNext(); ) {
            Triple<StopVisitType, String, OffsetTime> stopover = it.next();

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

    public boolean isMultiLegFlightRoute(List<Flight> flights) {
        return CollectionUtils.isNotEmpty(flights) && flights.size() > 1;
    }

    public boolean isDirectFlightRoute(List<Flight> flights) {
        return CollectionUtils.isNotEmpty(flights) && flights.size() == 1;
    }

    public List<Flight> findConnectingFlightLegs(Flight currentFlightLeg, Map<String, List<Flight>> flightsByDepartureAirportIata,
            Map<String, List<Flight>> flightsByArrivalAirportIata, Set<BigInteger> distinctFlightLegIds) {

        if (distinctFlightLegIds.contains(currentFlightLeg.getId())) {
            return Collections.emptyList();
        }

        List<Flight> connectingFlightLegs = Lists.newArrayList(currentFlightLeg);
        List<Flight> previousFlightLegs = findPreviousFlightLegs(currentFlightLeg, flightsByArrivalAirportIata, Lists.newArrayList());

        if (CollectionUtils.isNotEmpty(previousFlightLegs)) {
            connectingFlightLegs.addAll(previousFlightLegs);
        }

        List<Flight> nextFlightLegs = findNextFlightLegs(currentFlightLeg, flightsByDepartureAirportIata, Lists.newArrayList());

        if (CollectionUtils.isNotEmpty(nextFlightLegs)) {
            connectingFlightLegs.addAll(nextFlightLegs);
        }

        if (connectingFlightLegs.size() > 1) {
            connectingFlightLegs.sort(Comparator.comparing(Flight::getId));
            connectingFlightLegs.forEach(flight -> distinctFlightLegIds.add(flight.getId()));
        }

        return connectingFlightLegs;
    }

    public List<Flight> findPreviousFlightLegs(Flight currentFlightLeg, Map<String, List<Flight>> flightsByArrivalAirportIata, List<Flight> previousFlightLegs) {
        String departureAirportIata = currentFlightLeg.getDepartureStation();
        List<Flight> flightLegsForIata = flightsByArrivalAirportIata.get(departureAirportIata);
        Predicate<Flight> flightPredicate = FlightPredicate.matchPreviousFlight(currentFlightLeg);
        Flight optionalFlightLeg = findOptionalConnectingFlightLeg(flightPredicate, flightLegsForIata);

        if (optionalFlightLeg != null) {
            previousFlightLegs.add(optionalFlightLeg);
            findPreviousFlightLegs(optionalFlightLeg, flightsByArrivalAirportIata, previousFlightLegs);
        }

        return previousFlightLegs;
    }

    public List<Flight> findNextFlightLegs(Flight currentFlightLeg, Map<String, List<Flight>> flightsByDepartureAirportIata, List<Flight> nextFlightLegs) {
        String arrivalAirportIata = currentFlightLeg.getArrivalStation();
        List<Flight> flightLegsForIata = flightsByDepartureAirportIata.get(arrivalAirportIata);
        Predicate<Flight> flightPredicate = FlightPredicate.matchNextFlight(currentFlightLeg);
        Flight optionalFlightLeg = findOptionalConnectingFlightLeg(flightPredicate, flightLegsForIata);

        if (optionalFlightLeg != null) {
            nextFlightLegs.add(optionalFlightLeg);
            findNextFlightLegs(optionalFlightLeg, flightsByDepartureAirportIata, nextFlightLegs);
        }

        return nextFlightLegs;
    }

    public Flight findOptionalConnectingFlightLeg(Predicate<Flight> flightPredicate, List<Flight> flightLegs) {
        Optional<Flight> optionalStopoverFlight = flightLegs.stream()
                .filter(flightPredicate)
                .findFirst();
        return optionalStopoverFlight.orElse(null);
    }

    public List<Triple<StopVisitType, String, OffsetTime>> extractStopoversFromFlights(List<Flight> stopoverFlights) {
        List<Triple<StopVisitType, String, OffsetTime>> stopoverTriples = new ArrayList<>();

        stopoverFlights.forEach(stopoverFlight -> {
            Triple<StopVisitType, String, OffsetTime> departureTriple = new ImmutableTriple<>(
                    StopVisitType.DEPARTURE, stopoverFlight.getDepartureStation(), stopoverFlight.getStd());

            Triple<StopVisitType, String, OffsetTime> arrivalTriple = new ImmutableTriple<>(
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

    public ScheduledFlight createScheduledFlightWithStopovers(Flight currentFlight, List<ScheduledStopover> scheduledStopovers) {
        if (CollectionUtils.isNotEmpty(scheduledStopovers)) {
            ScheduledFlight scheduledFlight = new ScheduledFlight();
            scheduledFlight.setAirlineFlightId(currentFlight.getAirlineDesignator() + currentFlight.getFlightNumber());
            scheduledFlight.setAirlineIATA(currentFlight.getAirlineDesignator());
            scheduledFlight.setDateOfOperation(currentFlight.getDateOfOperation());
            scheduledFlight.getScheduledStopovers().addAll(scheduledStopovers);

            return scheduledFlight;
        }

        return null;
    }

    public ScheduledFlight convertToScheduledFlight(Flight flight, List<ScheduledStopover> scheduledStopovers) {
        ScheduledFlight scheduledFlight = new ScheduledFlight();
        scheduledFlight.setAirlineIATA(flight.getAirlineDesignator());
        scheduledFlight.setAirlineFlightId(flight.getAirlineDesignator() + flight.getFlightNumber());
        scheduledFlight.setDateOfOperation(flight.getDateOfOperation());

        if (CollectionUtils.isNotEmpty(scheduledStopovers)) {
            scheduledFlight.getScheduledStopovers().addAll(scheduledStopovers);
        } else {
            scheduledFlight.setFlightId(flight.getId());
            scheduledFlight.setDepartureAirportIATA(flight.getDepartureStation());
            scheduledFlight.setArrivalAirportIATA(flight.getArrivalStation());
            scheduledFlight.setTimeOfDeparture(flight.getStd());
            scheduledFlight.setTimeOfArrival(flight.getSta());
        }

        scheduledFlight.setStopsDesignation("");
        scheduledFlight.setTimesDesignation("");

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

}
