package no.rutebanken.extime.converter;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import no.rutebanken.extime.model.*;
import no.rutebanken.extime.util.AvinorTimetableUtils;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TypeConverter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigInteger;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static no.rutebanken.extime.Constants.*;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_HTTP_URI;
import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_URI_PARAMETERS;

@Component(value = "scheduledFlightConverter")
public class ScheduledFlightConverter {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledFlightConverter.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_PATTERN);

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

    public List<ScheduledFlight> convertToScheduledFlights(List<Flight> scheduledFlights) {

        // TODO For now we create a separate from- and to date in converter, but this should actually
        //      come as input headers from previous date range generator, to be 100% sure these are always the same
        LocalDate requestPeriodFromDate = LocalDate.now(ZoneId.of(DEFAULT_ZONE_ID));
        LocalDate requestPeriodToDate = requestPeriodFromDate.plusMonths(numberOfMonthsInPeriod);

        OffsetTime offsetMidnight = OffsetTime.parse(OFFSET_MIDNIGHT_UTC).withOffsetSameLocal(ZoneOffset.UTC);
        OffsetDateTime requestPeriodFromDateTime = requestPeriodFromDate.atTime(offsetMidnight);
        OffsetDateTime requestPeriodToDateTime = requestPeriodToDate.atTime(offsetMidnight);

        // TODO check for codeshare flights here, use the infrequent designator enumset group, to only check international airlines

        // filter section
        //List<Flight> filteredFlights = filterValidFlights(scheduledFlights);
        List<Flight> filteredFlights = scheduledFlights;

        // conversion section
        Map<String, List<Flight>> flightsByDepartureAirport = filteredFlights.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureStation));

        Map<String, List<Flight>> flightsByArrivalAirportIata = filteredFlights.stream()
                .collect(Collectors.groupingBy(Flight::getArrivalStation));

        Set<BigInteger> distinctFlightLegIds = Sets.newHashSet();
        List<ScheduledFlight> distinctFlights = Lists.newArrayList();
        List<ScheduledFlight> mergedScheduledFlights = Lists.newArrayList();

        for (Flight flight : filteredFlights) {
            List<Flight> connectingFlightLegs = findConnectingFlightLegs(flight, flightsByDepartureAirport, flightsByArrivalAirportIata, distinctFlightLegIds);

            if (CollectionUtils.isEmpty(connectingFlightLegs)) {
/*
                logger.debug("Flight with unique id: {}, and flightId: {} is part of multi leg flight, and already processed, skipping...",
                        flight.getId(),
                        flight.getAirlineDesignator(),
                        flight.getFlightNumber());
*/
                continue;
            }

            if (isMultiLegFlightRoute(connectingFlightLegs)) {
                List<Triple<StopVisitType, String, OffsetTime>> stopovers = extractStopoversFromFlights(connectingFlightLegs);
                List<ScheduledStopover> scheduledStopovers = createScheduledStopovers(stopovers);
                ScheduledFlight scheduledFlightWithStopovers = createScheduledFlightWithStopovers(requestPeriodFromDateTime, requestPeriodToDateTime, flight, scheduledStopovers);

                if (scheduledFlightWithStopovers != null) {
                    mergedScheduledFlights.add(scheduledFlightWithStopovers);
                }
            } else if (isDirectFlightRoute(connectingFlightLegs)) {
                ScheduledFlight directFlight = convertToScheduledFlight(flight, requestPeriodFromDateTime, requestPeriodToDateTime);
                mergedScheduledFlights.add(directFlight);
            } else {
                logger.error("Flight with unique id: {}, and flightId: {} is NOT a valid flight",
                        flight.getId(),
                        flight.getAirlineDesignator(),
                        flight.getFlightNumber());
                throw new RuntimeException("Invalid flight");
            }
        }

        // group by airline iata and unique lines
        Map<String, Map<String, List<ScheduledFlight>>> flightsByAirlineAndLine = mergedScheduledFlights.stream()
                .collect(Collectors.groupingBy(ScheduledFlight::getAirlineIATA,
                         Collectors.groupingBy(ScheduledFlight::getOperatingLine)));

        // find and merge all flights belonging to equivalent lines, and update map
        for (Map.Entry<String, Map<String, List<ScheduledFlight>>> entry : flightsByAirlineAndLine.entrySet()) {
            Map<String, List<ScheduledFlight>> lineIdFlightsMap = entry.getValue();
            Map<String, List<ScheduledFlight>> mergedLineFlights = findAndMergeFlightsByEquivalentLines(lineIdFlightsMap);
            flightsByAirlineAndLine.replace(entry.getKey(), mergedLineFlights);
        }

        System.out.println("WHAT DO WE HAVE HERE?");

        // TODO consider merging this with above iteration, when getting the merged map
        Map<String, List<ScheduledFlight>> wideroeLines = flightsByAirlineAndLine.get("WF");
        for (Map.Entry<String, List<ScheduledFlight>> lineEntry : wideroeLines.entrySet()) {
            if (lineEntry.getKey().equals("OSL-BGO<=>BGO-OSL")) {
                FlightLineDataSet flightLineDataSet = convertToFlightLineDataSet(requestPeriodFromDateTime, requestPeriodToDateTime, lineEntry);
            }
        }

        for (Map.Entry<String, Map<String, List<ScheduledFlight>>> entry : flightsByAirlineAndLine.entrySet()) {
            Map<String, List<ScheduledFlight>> lineIdFlightsMap = entry.getValue();
            //analyzeLines(lineIdFlightsMap);
        }

        Map<String, List<ScheduledFlight>> scheduledFlightsByFlightId = mergedScheduledFlights.stream()
                .sorted(Comparator.comparing(ScheduledFlight::getDateOfOperation))
                .collect(Collectors.groupingBy(ScheduledFlight::getAirlineFlightId));

        for (Map.Entry<String, List<ScheduledFlight>> entry : scheduledFlightsByFlightId.entrySet()) {
            String flightId = entry.getKey();
            List<ScheduledFlight> flights = entry.getValue();
            Set<DayOfWeek> daysOfWeek = findJourneyPatterns(flightId, flights);
            ScheduledFlight scheduledFlight = flights.get(0);
            scheduledFlight.setWeekDaysPattern(daysOfWeek);
            distinctFlights.add(scheduledFlight);
        }

        return distinctFlights.stream()
                .filter(distinctByKey(ScheduledFlight::getAirlineFlightId))
                .collect(Collectors.toList());
        //return distinctFlights;
    }

    private FlightLineDataSet convertToFlightLineDataSet(OffsetDateTime requestPeriodFromDateTime,
                                                         OffsetDateTime requestPeriodToDateTime,
                                                         Map.Entry<String, List<ScheduledFlight>> flightsByLineEntry) {

        String lineId = flightsByLineEntry.getKey();
        List<ScheduledFlight> flights = flightsByLineEntry.getValue();
        FlightLineDataSet flightLineDataSet = new FlightLineDataSet();

        // pre 1. set availability period

        AvailabilityPeriod availabilityPeriod = new AvailabilityPeriod(requestPeriodFromDateTime, requestPeriodToDateTime);
        flightLineDataSet.setAvailabilityPeriod(availabilityPeriod);

        // 1. find unique route patterns, every unique route maps to a journey pattern one-to-one for flights

        // get all unique route patterns for the current line
        Set<String> uniqueRoutePatterns = flights.stream()
                .map(ScheduledFlight::getRoutePattern)
                .collect(Collectors.toSet());

        // 2. add routes to dataset

        flightLineDataSet.setRoutePatterns(uniqueRoutePatterns);

        // 3. find journey patterns? same as routes...?
        // 4. add journey patterns to dataset

        flightLineDataSet.setJourneyPatterns(uniqueRoutePatterns);

        // 5. find the actual journeys per route pattern first, implemented/realized in flights, e.g. WF149

        // TODO consider having a separate object wrapper; RoutePattern, holding the pattern string and other useful properties

        Map<String, Map<String, List<ScheduledFlight>>> journeysByRouteAndFlightId = flights.stream()
                .collect(Collectors.groupingBy(ScheduledFlight::getRoutePattern,
                        Collectors.groupingBy(ScheduledFlight::getAirlineFlightId)));

        // 6. add journeys to dataset

        flightLineDataSet.setRouteJourneys(journeysByRouteAndFlightId);

        // 7. find day/period patterns for each journey

        // 8. add the day/period patterns to dataset, and also add referentials

        return flightLineDataSet;
    }

    private Map<String, List<ScheduledFlight>> findAndMergeFlightsByEquivalentLines(Map<String, List<ScheduledFlight>> flightsByUniqueLineId) {
        Set<String> lineIds = new HashSet<>();
        Map<String, List<ScheduledFlight>> mergedFlightsByLine = new HashMap<>();

        for (String lineId : flightsByUniqueLineId.keySet()) {
            if (!lineIds.contains(lineId)) {
                List<ScheduledFlight> lineFlights = flightsByUniqueLineId.get(lineId);
                Joiner joiner = Joiner.on(EQUIVALENT_SYMBOL).skipNulls();
                String oppositeLineId = getOppositeLineId(lineId);
                String lineIdMergedKey = joiner.join(lineId, oppositeLineId);

                if (!lineId.equals(oppositeLineId)) {
                    if (flightsByUniqueLineId.containsKey(oppositeLineId)) {
                        List<ScheduledFlight> oppositeLineFlights = flightsByUniqueLineId.get(oppositeLineId);

                        List<ScheduledFlight> mergedFlights = Stream.of(lineFlights, oppositeLineFlights)
                                .flatMap(Collection::stream)
                                .collect(Collectors.toList());

                        mergedFlightsByLine.put(lineIdMergedKey, mergedFlights);
                        lineIds.addAll(Arrays.asList(lineId, oppositeLineId));
                    } else {
                        mergedFlightsByLine.put(joiner.join(lineId, lineId), lineFlights);
                        lineIds.add(lineId);
                    }
                } else {
                    mergedFlightsByLine.put(lineIdMergedKey, lineFlights);
                    lineIds.add(lineId);
                }
            }
        }

        return mergedFlightsByLine;
    }

    private String getOppositeLineId(String lineId) {
        List<String> lineAirportIatas = Splitter.on(DASH)
                .trimResults()
                .omitEmptyStrings()
                .limit(2)
                .splitToList(lineId);
        return Joiner.on(DASH).skipNulls().join(Lists.reverse(lineAirportIatas));
    }

    private <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> map = new ConcurrentHashMap<>();
        return t -> map.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    private <T> Predicate<T> distinctByKeyNoParallel(Function<? super T, ?> keyExtractor) {
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

    public ScheduledFlight createScheduledFlightWithStopovers(OffsetDateTime requestPeriodFromDateTime, OffsetDateTime requestPeriodToDateTime,
                                                              Flight currentFlight, List<ScheduledStopover> scheduledStopovers) {
        if (CollectionUtils.isNotEmpty(scheduledStopovers)) {
            ScheduledFlight scheduledFlight = new ScheduledFlight();
            scheduledFlight.setAirlineFlightId(String.format("%s%s",
                    currentFlight.getAirlineDesignator(), currentFlight.getFlightNumber()));
            scheduledFlight.setAirlineIATA(currentFlight.getAirlineDesignator());
            scheduledFlight.setAvailabilityPeriod(new AvailabilityPeriod(requestPeriodFromDateTime, requestPeriodToDateTime));
            scheduledFlight.setDateOfOperation(currentFlight.getDateOfOperation());
            scheduledFlight.getScheduledStopovers().addAll(scheduledStopovers);

            return scheduledFlight;
        }

        return null;
    }

    public boolean isMultiLegFlightRoute(List<Flight> flights) {
        return CollectionUtils.isNotEmpty(flights) && flights.size() > 1;
    }

    public boolean isDirectFlightRoute(List<Flight> flights) {
        return CollectionUtils.isNotEmpty(flights) && flights.size() == 1;
    }

    /**
     * @todo: handle different departure times, i.e. on weekends (will be used as separate ServiceJourneys in netex)
     *
     * For example, flight WF149 between Oslo and Bergen, operates every work day (man-fri) with departure time 14:05
     * but also one day at the weekend, sunday, with departure time at 14:00
     *
     * Also, one flight can operate with one route (i.e. OSL-HOV-SOG-BGO) some days, and another route (i.e. OSL-HOV-BGO) some other day, support!
     */
    private Set<DayOfWeek> findJourneyPatterns(String flightId, List<? extends ScheduledFlight> flights) {
        SortedSet<DayOfWeek> daysOfWeek = new TreeSet<>();
        LocalDate periodStartDate = flights.get(0).getDateOfOperation();
        LocalDate periodEndDate = periodStartDate.plusWeeks(1L);

        for (ScheduledFlight flight : flights) {
            if (flight.getDateOfOperation().isAfter(periodEndDate)) {
                break;
            } else {
                LocalDate dateOfOperation = flight.getDateOfOperation();
                DayOfWeek dayOfWeek = dateOfOperation.getDayOfWeek();
                daysOfWeek.add(dayOfWeek);
            }
        }

        return daysOfWeek;
    }

    // TODO: We must take into consideration that it is possible to have multiple journey patterns or routes for a specific flight id
    // like flight WF739 which can take the following route/journey pattern one day: BOO-OSY-TRD, and this route/journey pattern another day: BOO-MJF-OSY-TRD
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

    public ScheduledFlight convertToScheduledFlight(Flight flight, OffsetDateTime fromDateTime, OffsetDateTime toDateTime) {
        ScheduledFlight scheduledFlight = new ScheduledFlight();
        scheduledFlight.setFlightId(flight.getId());
        scheduledFlight.setAirlineIATA(flight.getAirlineDesignator());
        scheduledFlight.setAirlineFlightId(String.format("%s%s", flight.getAirlineDesignator(), flight.getFlightNumber()));
        scheduledFlight.setAvailabilityPeriod(new AvailabilityPeriod(fromDateTime, toDateTime));
        scheduledFlight.setDateOfOperation(flight.getDateOfOperation());
        scheduledFlight.setDepartureAirportIATA(flight.getDepartureStation());
        scheduledFlight.setArrivalAirportIATA(flight.getArrivalStation());
        scheduledFlight.setTimeOfDeparture(flight.getStd());
        scheduledFlight.setTimeOfArrival(flight.getSta());
        return scheduledFlight;
    }

}
