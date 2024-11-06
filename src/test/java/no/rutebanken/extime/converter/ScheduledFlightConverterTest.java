package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import no.rutebanken.extime.config.NetexStaticDataSet;
import no.rutebanken.extime.model.FlightLeg;
import no.rutebanken.extime.model.FlightLegBuilder;
import no.rutebanken.extime.model.LineDataSet;
import no.rutebanken.extime.model.StopVisitType;
import no.rutebanken.extime.util.DateUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static no.rutebanken.extime.TestUtils.*;

class ScheduledFlightConverterTest {

    private static final ZonedDateTime LEG1_SCHEDULED_DEPARTURE_TIME = ZonedDateTime.of(2024, 1, 1, 1, 0, 0, 0, ZoneId.of("CET"));
    private static final ZonedDateTime LEG1_SCHEDULED_ARRIVAL_TIME = ZonedDateTime.of(2024, 1, 1, 2, 0, 0, 0, ZoneId.of("CET"));
    private static final ZonedDateTime LEG2_SCHEDULED_DEPARTURE_TIME = ZonedDateTime.of(2024, 1, 1, 3, 0, 0, 0, ZoneId.of("CET"));
    private static final ZonedDateTime LEG2_SCHEDULED_ARRIVAL_TIME = ZonedDateTime.of(2024, 1, 1, 4, 0, 0, 0, ZoneId.of("CET"));

    private ScheduledFlightConverter clazzUnderTest;

    @BeforeEach
    void setUp() {
        NetexStaticDataSet netexStaticDataSet = new NetexStaticDataSet();

        NetexStaticDataSet.StopPlaceDataSet tos = new NetexStaticDataSet.StopPlaceDataSet();
        tos.setShortName("Tromsø");
        NetexStaticDataSet.StopPlaceDataSet boo = new NetexStaticDataSet.StopPlaceDataSet();
        tos.setShortName("Bodø");
        NetexStaticDataSet.StopPlaceDataSet trd = new NetexStaticDataSet.StopPlaceDataSet();
        tos.setShortName("Trondheim");

        Map<String, NetexStaticDataSet.StopPlaceDataSet> stopPlaces = Map.of(
                "tos", tos,
                "boo", boo,
                "trd", trd
        );
        netexStaticDataSet.setStopPlaces(stopPlaces);
        DateUtils dateUtils = new DateUtils(Duration.ofDays(14), ZoneId.of("CET"));
        clazzUnderTest = new ScheduledFlightConverter(netexStaticDataSet, dateUtils);
    }

    @Test
    void convertSingleLegFlight() {
        FlightLeg leg1 = new FlightLegBuilder()
                .withDepartureAirport("TOS")
                .withArrivalAirport("BOO")
                .withAirlineDesignator("DY")
                .withFlightNumber("DY1")
                .withStd(LEG1_SCHEDULED_DEPARTURE_TIME)
                .withSta(LEG1_SCHEDULED_ARRIVAL_TIME)
                .build();
        List<FlightLeg> flightLegs = List.of(leg1);
        List<LineDataSet> lineDataSets = clazzUnderTest.convertFlightLegsToLineCentricDataSets(flightLegs);
        Assertions.assertThat(lineDataSets)
                .isNotNull()
                .isNotEmpty();
        LineDataSet lineDataSet = lineDataSets.getFirst();
        Assertions.assertThat(lineDataSet.getLineDesignation()).isEqualTo("BOO-TOS");
        Assertions.assertThat(lineDataSet.getAirlineIata()).isEqualTo("DY");
    }


    @Test
    void convertMultiLegFlight() {
        FlightLeg leg1 = new FlightLegBuilder()
                .withDepartureAirport("TOS")
                .withArrivalAirport("BOO")
                .withAirlineDesignator("DY")
                .withFlightNumber("DY1")
                .withStd(LEG1_SCHEDULED_DEPARTURE_TIME)
                .withSta(LEG1_SCHEDULED_ARRIVAL_TIME)
                .build();
        FlightLeg leg2 = new FlightLegBuilder()
                .withDepartureAirport("BOO")
                .withArrivalAirport("TRD")
                .withAirlineDesignator("DY")
                .withFlightNumber("DY1")
                .withStd(LEG2_SCHEDULED_DEPARTURE_TIME)
                .withSta(LEG2_SCHEDULED_ARRIVAL_TIME)
                .build();
        List<FlightLeg> flightLegs = List.of(leg1, leg2);
        List<LineDataSet> lineDataSets = clazzUnderTest.convertFlightLegsToLineCentricDataSets(flightLegs);
        Assertions.assertThat(lineDataSets)
                .isNotNull()
                .isNotEmpty();
        LineDataSet lineDataSet = lineDataSets.getFirst();
        Assertions.assertThat(lineDataSet.getLineDesignation()).isEqualTo("TRD-TOS");
        Assertions.assertThat(lineDataSet.getAirlineIata()).isEqualTo("DY");
    }

    @Test
    void extractNoStopoversFromFlights() {
        List<Triple<StopVisitType, String, LocalTime>> triples =
                clazzUnderTest.extractStopoversFromFlights(Collections.emptyList());
        Assertions.assertThat(triples)
                .isNotNull()
                .isEmpty();
    }

    @Test
    void extractStopoversFromFlights() {
        List<Triple<StopVisitType, String, LocalTime>> triples =
                clazzUnderTest.extractStopoversFromFlights(createDummyFlights());

        Assertions.assertThat(triples)
                .isNotNull()
                .isNotEmpty()
                .hasSize(6);

        Assertions.assertThat(triples.get(0).getLeft())
                .isEqualTo(StopVisitType.DEPARTURE);
        Assertions.assertThat(triples.get(0).getMiddle())
                .isEqualTo("BGO");

        Assertions.assertThat(triples.get(5).getLeft())
                .isEqualTo(StopVisitType.ARRIVAL);
        Assertions.assertThat(triples.get(5).getMiddle())
                .isEqualTo("SVG");
    }

    @Test
    void testIsMultiLegFlightRoute() {
        List<FlightLeg> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", "HOV",
                        ZDT_2017_01_01_07_00, "SOG", ZDT_2017_01_01_07_30),
                createFlight(1003L, "WF", "149", "SOG",
                        ZDT_2017_01_01_08_00, "BGO", ZDT_2017_01_01_08_30)
        );

        boolean isMultiLegFlightRoute = clazzUnderTest.isMultiLegFlightRoute(flightLegs);

        Assertions.assertThat(isMultiLegFlightRoute).isTrue();
    }

    @Test
    void testIsNotMultiLegFlightRoute() {
        List<FlightLeg> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", "HOV",
                        ZDT_2017_01_01_07_00, "SOG", ZDT_2017_01_01_07_30)
        );

        boolean isMultiLegFlightRoute = clazzUnderTest.isMultiLegFlightRoute(flightLegs);

        Assertions.assertThat(isMultiLegFlightRoute).isFalse();
    }

    @Test
    void testIsDirectFlightRoute() {
        List<FlightLeg> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", "HOV",
                        ZDT_2017_01_01_07_00, "SOG", ZDT_2017_01_01_07_30)
        );

        boolean isDirectFlightRoute = clazzUnderTest.isDirectFlightRoute(flightLegs);

        Assertions.assertThat(isDirectFlightRoute).isTrue();
    }

    @Test
    void testIsNotDirectFlightRoute() {
        List<FlightLeg> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", "HOV",
                        ZDT_2017_01_01_07_00, "SOG", ZDT_2017_01_01_07_30),
                createFlight(1003L, "WF", "149", "SOG",
                        ZDT_2017_01_01_08_00, "BGO", ZDT_2017_01_01_08_30)
        );

        boolean isDirectFlightRoute = clazzUnderTest.isDirectFlightRoute(flightLegs);

        Assertions.assertThat(isDirectFlightRoute).isFalse();
    }


    @Test
    void testCollectedFlightLegIds() {
        List<FlightLeg> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", "HOV",
                        ZDT_2017_01_01_07_00, "SOG", ZDT_2017_01_01_07_30),
                createFlight(1003L, "WF", "149", "SOG",
                        ZDT_2017_01_01_08_00, "BGO", ZDT_2017_01_01_08_30),
                createFlight(9999L, "SK", "4455", "TRD",
                        ZDT_2017_01_02_08_00, "OSL", ZDT_2017_01_02_08_30),
                createFlight(8888L, "DY", "8899", "OSL",
                        ZDT_2017_01_03_08_00, "HOV", ZDT_2017_01_03_08_30),
                createFlight(7777L, "M3", "566", "BGO",
                        ZDT_2017_01_03_08_00, "TRD", ZDT_2017_01_03_08_30)
        );
        FlightLeg currentFlight = createFlight(1001L, "WF", "149", "OSL",
                ZDT_2017_01_01_06_00, "HOV", ZDT_2017_01_01_06_30);

        Map<String, List<FlightLeg>> flightsByDepartureAirport = flightLegs.stream()
                .collect(Collectors.groupingBy(FlightLeg::getDepartureAirport));

        HashSet<Long> distinctFlightLegIds = Sets.newHashSet();

        clazzUnderTest.findConnectingFlightLegs(
                currentFlight, flightsByDepartureAirport, distinctFlightLegIds);

        Assertions.assertThat(distinctFlightLegIds)
                .isNotNull()
                .isNotEmpty()
                .hasSize(3)
                .containsOnly(1001L, 1002L, 1003L);
    }

    @Test
    void testFindConnectingFlightLegsForFirstLeg() {
        List<FlightLeg> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", "HOV",
                        ZDT_2017_01_01_07_00, "SOG", ZDT_2017_01_01_07_30),
                createFlight(1003L, "WF", "149", "SOG",
                        ZDT_2017_01_01_08_00, "BGO", ZDT_2017_01_01_08_30),
                createFlight(9999L, "SK", "4455", "TRD",
                        ZDT_2017_01_01_08_00, "OSL", ZDT_2017_01_01_08_30),
                createFlight(8888L, "DY", "8899", "OSL",
                        ZDT_2017_01_01_08_00, "HOV", ZDT_2017_01_01_08_30),
                createFlight(7777L, "M3", "566", "BGO",
                        ZDT_2017_01_01_08_00, "TRD", ZDT_2017_01_01_08_30)
        );
        FlightLeg currentFlight = createFlight(1001L, "WF", "149", "OSL",
                ZDT_2017_01_01_06_00, "HOV", ZDT_2017_01_01_06_30);

        Map<String, List<FlightLeg>> flightsByDepartureAirport = flightLegs.stream()
                .collect(Collectors.groupingBy(FlightLeg::getDepartureAirport));

        List<FlightLeg> connectingFlightLegs = clazzUnderTest.findConnectingFlightLegs(
                currentFlight, flightsByDepartureAirport, Sets.newHashSet());

        Assertions.assertThat(connectingFlightLegs)
                .isNotNull()
                .isNotEmpty()
                .hasSize(3)
                .containsSequence(currentFlight, flightLegs.get(0), flightLegs.get(1));
    }


    @Test
    void testFindNextFlightLegsForLastLeg() {
        List<FlightLeg> flightLegs = Lists.newArrayList(
                createFlight(1099L, "SK", "4455", "BGO",
                        ZDT_2017_01_01_07_00, "OSL", ZDT_2017_01_01_07_30)
        );
        FlightLeg currentFlight = createFlight(1003L, "WF", "149", "SOG",
                ZDT_2017_01_01_06_00, "BGO", ZDT_2017_01_01_06_30);

        Map<String, List<FlightLeg>> flightsByDepartureAirport = flightLegs.stream()
                .collect(Collectors.groupingBy(FlightLeg::getDepartureAirport));

        List<FlightLeg> nextFlightLegs = clazzUnderTest.findNextFlightLegs(
                currentFlight, flightsByDepartureAirport, Lists.newLinkedList());

        Assertions.assertThat(nextFlightLegs)
                .isNotNull()
                .isEmpty();
    }

    @Test
    void testFindNextFlightLegs() {
        List<FlightLeg> flightLegs = Lists.newArrayList(
                createFlight(1002L, "WF", "149", "HOV",
                        ZDT_2017_01_01_09_00, "SOG", ZDT_2017_01_01_09_30),
                createFlight(1003L, "WF", "149", "SOG",
                        ZDT_2017_01_01_10_00, "BGO", ZDT_2017_01_01_10_30),
                createFlight(1004L, "WF", "148", "BGO",
                        ZDT_2017_01_01_06_00, "SOG", ZDT_2017_01_01_06_30)
        );
        FlightLeg currentFlight = createFlight(1001L, "WF", "149", "OSL",
                ZDT_2017_01_01_08_00, "HOV", ZDT_2017_01_01_08_30);

        Map<String, List<FlightLeg>> flightsByDepartureAirport = flightLegs.stream()
                .collect(Collectors.groupingBy(FlightLeg::getDepartureAirport));

        List<FlightLeg> nextFlightLegs = clazzUnderTest.findNextFlightLegs(
                currentFlight, flightsByDepartureAirport, Lists.newLinkedList());

        Assertions.assertThat(nextFlightLegs)
                .isNotNull()
                .isNotEmpty()
                .hasSize(2)
                .containsOnly(flightLegs.get(0), flightLegs.get(1));
    }




    private List<FlightLeg> createDummyFlights() {
        return Lists.newArrayList(
                createFlight(1L, "SK", "4455", "BGO", ZDT_2017_01_01_00_00, "OSL", ZDT_2017_01_01_23_59),
                createFlight(2L, "DY", "6677", "BGO", ZDT_2017_01_01_00_00, "TRD", ZDT_2017_01_01_23_59),
                createFlight(3L, "WF", "199", "BGO", ZDT_2017_01_01_00_00, "SVG", ZDT_2017_01_01_23_59)
        );
    }


}