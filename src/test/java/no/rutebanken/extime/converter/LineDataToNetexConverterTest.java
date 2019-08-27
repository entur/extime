package no.rutebanken.extime.converter;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import no.rutebanken.extime.Constants;
import no.rutebanken.extime.ExtimeRouteBuilderIntegrationTestBase;
import no.rutebanken.extime.config.CamelRouteDisabler;
import no.rutebanken.extime.fixtures.LineDataSetFixture;
import no.rutebanken.extime.model.FlightRoute;
import no.rutebanken.extime.model.LineDataSet;
import no.rutebanken.extime.util.NetexObjectIdCreator;
import no.rutebanken.extime.util.NetexObjectIdTypes;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rutebanken.netex.model.AllVehicleModesOfTransportEnumeration;
import org.rutebanken.netex.model.Common_VersionFrameStructure;
import org.rutebanken.netex.model.DayOfWeekEnumeration;
import org.rutebanken.netex.model.DayType;
import org.rutebanken.netex.model.DayTypeAssignment;
import org.rutebanken.netex.model.DestinationDisplay;
import org.rutebanken.netex.model.JourneyPattern;
import org.rutebanken.netex.model.JourneyPatternsInFrame_RelStructure;
import org.rutebanken.netex.model.Line;
import org.rutebanken.netex.model.LinkSequence_VersionStructure;
import org.rutebanken.netex.model.OperatingPeriod_VersionStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.Route;
import org.rutebanken.netex.model.RouteRefStructure;
import org.rutebanken.netex.model.RoutesInFrame_RelStructure;
import org.rutebanken.netex.model.ServiceCalendarFrame;
import org.rutebanken.netex.model.ServiceFrame;
import org.rutebanken.netex.model.ServiceJourney;
import org.rutebanken.netex.model.StopPointInJourneyPattern;
import org.rutebanken.netex.model.TimetableFrame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.xml.bind.JAXBElement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static no.rutebanken.extime.Constants.*;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
@ActiveProfiles("test")
@SpringBootTest(classes = {CamelRouteDisabler.class, LineDataToNetexConverter.class}, properties = "spring.config.name=application,netex-static-data")
public class LineDataToNetexConverterTest extends ExtimeRouteBuilderIntegrationTestBase {

    @Autowired
    private LineDataToNetexConverter netexConverter;

    @Test
    public void verifyFrameAttributes() throws Exception {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-BGO", 1), Pair.of("BGO-OSL", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("DY", "OSL-BGO", routeJourneyPairs);
        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();

        NetexTestUtils.verifyCompositeFrameAttributes(publicationDelivery);
        NetexTestUtils.verifyServiceFrameAttributes(publicationDelivery);

        TimetableFrame timetableFrame = NetexTestUtils.getFrames(TimetableFrame.class, NetexTestUtils.getDataObjectFrames(publicationDelivery)).get(0);
        assertThat(timetableFrame).hasFieldOrPropertyWithValue("version", VERSION_ONE);
        assertThat(timetableFrame.getId()).matches(id -> id.split(":")[1].equals(NetexObjectIdTypes.TIMETABLE_FRAME_KEY), "TimetableFrame");

        ServiceCalendarFrame serviceCalendarFrame = NetexTestUtils.getFrames(ServiceCalendarFrame.class, NetexTestUtils.getDataObjectFrames(publicationDelivery)).get(0);
        assertThat(serviceCalendarFrame).hasFieldOrPropertyWithValue("version", VERSION_ONE);
        assertThat(serviceCalendarFrame.getId()).matches(id -> id.split(":")[1].equals(NetexObjectIdTypes.SERVICE_CALENDAR_FRAME_KEY), "ServiceCalendarFrame");
    }

    @Test
    public void testLineWithRoundTripRoutes() throws Exception {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-BGO", 1), Pair.of("BGO-OSL", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("DY", "OSL-BGO", routeJourneyPairs);

        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = NetexTestUtils.getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = NetexTestUtils.getFrames(ServiceFrame.class, dataObjectFrames).get(0);

        Line line = (Line) serviceFrame.getLines().getLine_().get(0).getValue();

        assertValidLine(line, lineDataSet);
        assertValidRoutes(serviceFrame.getRoutes(), lineDataSet, line);
    }

    @Test
    public void testLineWithStopoverRoutes() throws Exception {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("TRD-OSL-BGO-MOL-SOG", 1), Pair.of("SOG-MOL-BGO-OSL-TRD", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("WF", "TRD-SOG", routeJourneyPairs);

        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = NetexTestUtils.getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = NetexTestUtils.getFrames(ServiceFrame.class, dataObjectFrames).get(0);

        Line line = (Line) serviceFrame.getLines().getLine_().get(0).getValue();

        assertValidLine(line, lineDataSet);
        assertValidRoutes(serviceFrame.getRoutes(), lineDataSet, line);
    }

    @Test
    public void testDestinationDisplaysNoVias() throws Exception {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-BGO", 1), Pair.of("BGO-OSL", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("DY", "OSL-BGO", routeJourneyPairs);

        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = NetexTestUtils.getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = NetexTestUtils.getFrames(ServiceFrame.class, dataObjectFrames).get(0);

        // check journey patterns for destination display reference
        JourneyPatternsInFrame_RelStructure journeyPatternStruct = serviceFrame.getJourneyPatterns();
        assertValidJourneyPatterns(journeyPatternStruct, lineDataSet);

        // check destination displays
        List<DestinationDisplay> destinationDisplays = serviceFrame.getDestinationDisplays().getDestinationDisplay();

        assertThat(destinationDisplays).extracting("version").contains(VERSION_ONE);

        assertThat(destinationDisplays)
                .hasSize(4)
                .extracting("id")
                .contains("AVI:DestinationDisplay:483298715", "AVI:DestinationDisplay:523288933",
                        "AVI:DestinationDisplay:DYOSLBGO-OSL", "AVI:DestinationDisplay:DYOSLBGO-BGO");

        for (DestinationDisplay destinationDisplay : destinationDisplays) {
            if (destinationDisplay.getId().equals("AVI:DestinationDisplay:DY_OSL-BGO")) {
                assertThat(destinationDisplay.getFrontText().getValue()).isEqualTo("Bergen");
            }
            if (destinationDisplay.getId().equals("AVI:DestinationDisplay:DY_BGO-OSL")) {
                assertThat(destinationDisplay.getFrontText().getValue()).isEqualTo("Oslo");
            }
        }
    }

    @Test
    public void testDestinationDisplaysWithVias() throws Exception {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-SOG-BGO", 1), Pair.of("BGO-SOG-OSL", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("DY", "OSL-BGO", routeJourneyPairs);

        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = NetexTestUtils.getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = NetexTestUtils.getFrames(ServiceFrame.class, dataObjectFrames).get(0);

        // check journey patterns for destination display reference
        JourneyPatternsInFrame_RelStructure journeyPatternStruct = serviceFrame.getJourneyPatterns();
        assertValidJourneyPatterns(journeyPatternStruct, lineDataSet);

        // check destination displays
        List<DestinationDisplay> destinationDisplays = serviceFrame.getDestinationDisplays().getDestinationDisplay();

        assertThat(destinationDisplays).extracting("version").contains(VERSION_ONE);

        assertThat(destinationDisplays)
                .hasSize(5)
                .extracting("id")
                .contains("AVI:DestinationDisplay:1646459719", "AVI:DestinationDisplay:467802439",
                        "AVI:DestinationDisplay:DYOSLBGO-OSL", "AVI:DestinationDisplay:DYOSLBGO-SOG", "AVI:DestinationDisplay:DYOSLBGO-BGO");

        for (DestinationDisplay destinationDisplay : destinationDisplays) {
            if (destinationDisplay.getId().equals("AVI:DestinationDisplay:DY_OSL-SOG-BGO")) {
                assertThat(destinationDisplay.getFrontText().getValue()).isEqualTo("Bergen");
                assertThat(destinationDisplay.getVias().getVia()).hasSize(1);
                assertThat(destinationDisplay.getVias().getVia().get(0).getDestinationDisplayRef().getVersion()).isEqualTo(VERSION_ONE);
                assertThat(destinationDisplay.getVias().getVia().get(0).getDestinationDisplayRef().getRef()).isEqualTo("AVI:DestinationDisplay:DYOSLBGO-SOG");
            }
            if (destinationDisplay.getId().equals("AVI:DestinationDisplay:DY_BGO-SOG-OSL")) {
                assertThat(destinationDisplay.getFrontText().getValue()).isEqualTo("Oslo");
                assertThat(destinationDisplay.getVias().getVia()).hasSize(1);
                assertThat(destinationDisplay.getVias().getVia().get(0).getDestinationDisplayRef().getVersion()).isEqualTo(VERSION_ONE);
                assertThat(destinationDisplay.getVias().getVia().get(0).getDestinationDisplayRef().getRef()).isEqualTo("AVI:DestinationDisplay:DYOSLBGO-SOG");
            }
        }
    }

    @Test
    public void testServiceJourneyWithOperatingPeriodAndExclusions() throws Exception {
        LocalDate patternFrom = LocalDate.of(2017, 1, 3);
        LocalDate patternTo = patternFrom.plusDays(70);
        Set<DayOfWeek> pattern = Sets.newHashSet(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
        // Pattern = 5 days a week, one exception at day 10
        List<LocalDate> flightDates = generatePattern(patternFrom, patternTo, pattern, 10);
        List<Pair<String, List<LocalDate>>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-SOG", flightDates));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSetWithFixedDates("DY", "OSL-BGO", routeJourneyPairs, ZonedDateTime.now().toLocalTime());

        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = NetexTestUtils.getDataObjectFrames(publicationDelivery);

        ServiceCalendarFrame serviceCalendarFrame = NetexTestUtils.getFrames(ServiceCalendarFrame.class, dataObjectFrames).get(0);
        Map<String, DayType> dayTypes = serviceCalendarFrame.getDayTypes().getDayType_().stream()
                .collect(Collectors.toMap(d -> d.getValue().getId(), d -> (DayType) d.getValue()));
        Map<String, DayTypeAssignment> dayTimeAssignments = serviceCalendarFrame.getDayTypeAssignments().getDayTypeAssignment().stream()
                .collect(Collectors.toMap(d -> d.getDayTypeRef().getValue().getRef(), d -> d));
        Map<String, OperatingPeriod_VersionStructure> operatingPeriods = serviceCalendarFrame.getOperatingPeriods().getOperatingPeriodOrUicOperatingPeriod()
                .stream().collect(Collectors.toMap(o -> o.getId(), o -> o));

        TimetableFrame timetableFrame = NetexTestUtils.getFrames(TimetableFrame.class, dataObjectFrames).get(0);
        ServiceJourney serviceJourney = (ServiceJourney) timetableFrame.getVehicleJourneys().getDatedServiceJourneyOrDeadRunOrServiceJourney().get(0);

        List<String> dayTypeRefs = serviceJourney.getDayTypes().getDayTypeRef().stream().map(e ->
                e.getValue().getRef()).collect(Collectors.toList());

        Set<LocalDate> expectedExclusions = Sets.newHashSet(patternFrom.plusDays(10));

        boolean weekDayPatternFound = false;
        boolean saturdayPatternFound = false;

        for (String dayTypeRef : dayTypeRefs) {
            DayTypeAssignment assignment = dayTimeAssignments.get(dayTypeRef);
            Assert.assertNotNull(assignment);
            DayType dayType = dayTypes.get(assignment.getDayTypeRef().getValue().getRef());
            Assert.assertNotNull(dayType);

            if (assignment.getDate() == null) {
                OperatingPeriod_VersionStructure operatingPeriod = operatingPeriods.get(assignment.getOperatingPeriodRef().getRef());
                Assert.assertNotNull(operatingPeriod);
                Assert.assertEquals(patternFrom.atStartOfDay(), operatingPeriod.getFromDate());
                Assert.assertEquals(patternTo.atStartOfDay(), operatingPeriod.getToDate());

                weekDayPatternFound |= ListUtils.isEqualList(
                        dayType.getProperties().getPropertyOfDay().get(0).getDaysOfWeek(), Arrays.asList(DayOfWeekEnumeration.MONDAY,
                                DayOfWeekEnumeration.TUESDAY, DayOfWeekEnumeration.WEDNESDAY,
                                DayOfWeekEnumeration.THURSDAY, DayOfWeekEnumeration.FRIDAY));
                saturdayPatternFound |= ListUtils.isEqualList(
                        dayType.getProperties().getPropertyOfDay().get(0).getDaysOfWeek(), Arrays.asList(DayOfWeekEnumeration.SATURDAY));
            } else {
                Assert.assertFalse(assignment.isIsAvailable());
                Assert.assertNull(assignment.getOperatingPeriodRef());
                Assert.assertTrue(expectedExclusions.remove(assignment.getDate().toLocalDate()));
            }
        }
        Assert.assertTrue("Did not find expected pattern for week days", weekDayPatternFound);
        Assert.assertTrue("Did not find expected pattern for saturdays", saturdayPatternFound);
        Assert.assertTrue("Not all expected exclusion dates found", expectedExclusions.isEmpty());
    }

    private List<LocalDate> generatePattern(LocalDate start, LocalDate end, Set<DayOfWeek> daysOfWeek, int... exclusionArray) {
        List<LocalDate> patternDates = new ArrayList<>();
        Set<Integer> exclusions = new HashSet<>();
        if (exclusionArray != null) {
            Arrays.stream(exclusionArray).forEach(e -> exclusions.add(e));
        }

        LocalDate current = start;
        int i = 0;
        while (!current.isAfter(end)) {
            if (!exclusions.contains(i++) && daysOfWeek.contains(current.getDayOfWeek())) {
                patternDates.add(current);
            }
            current = current.plusDays(1);
        }
        return patternDates;
    }

    private void assertValidPublicationDelivery(PublicationDeliveryStructure publicationDelivery, String lineName) {
        assertThat(publicationDelivery).isNotNull();
        assertThat(publicationDelivery.getVersion()).isEqualTo(NETEX_PROFILE_VERSION);
        assertThat(publicationDelivery.getPublicationTimestamp()).isNotNull().isBeforeOrEqualTo(ZonedDateTime.now(ZoneId.of(Constants.DEFAULT_ZONE_ID)).toLocalDateTime());
        assertThat(publicationDelivery.getParticipantRef()).isEqualTo("Avinor");
        assertThat(publicationDelivery.getDescription()).isNotNull();
        assertThat(publicationDelivery.getDescription().getValue()).isEqualTo("Line: " + lineName);
        assertThat(publicationDelivery.getDataObjects()).isNotNull();
    }

/*
    private void assertValidNetwork(Network network, String airlineIata) {
        assertThat(network).isNotNull();
        assertThat(network.getId()).isEqualTo(String.format("AVI:Network:%s", airlineIata));
        assertThat(network.getName().getValue()).isEqualTo(LineDataSetFixture.getAirlineName(airlineIata));
    }
*/

    private void assertValidLine(Line line, LineDataSet lineDataSet) {
        assertThat(line.getId()).isEqualTo(String.format("AVI:Line:%s_%s", lineDataSet.getAirlineIata(), lineDataSet.getLineDesignation()));
        assertThat(line.getName().getValue()).isEqualTo(lineDataSet.getLineName());
        assertThat(line.getTransportMode()).isEqualTo(AllVehicleModesOfTransportEnumeration.AIR);
       // assertThat(line.getPublicCode()).isEqualTo(lineDataSet.getLineDesignation());
        assertThat(line.getOperatorRef().getRef()).isEqualTo(String.format("AVI:Operator:%s", lineDataSet.getAirlineIata()));
        assertThat(line.getRepresentedByGroupRef().getRef()).isEqualTo(String.format("AVI:Network:%s", lineDataSet.getAirlineIata()));

        assertThat(line.getRoutes().getRouteRef())
                .hasSize(lineDataSet.getFlightRoutes().size())
                .extracting(RouteRefStructure::getRef)
                .containsExactlyElementsOf(getRouteIds(lineDataSet));
    }

    private void assertValidRoutes(RoutesInFrame_RelStructure routeStruct, LineDataSet lineDataSet, Line line) {
        assertThat(routeStruct).isNotNull();
        List<JAXBElement<? extends LinkSequence_VersionStructure>> routeElements = routeStruct.getRoute_();

        List<Route> routes = routeElements.stream()
                .map(JAXBElement::getValue)
                .map(route -> (Route) route)
                .collect(Collectors.toList());
        assertThat(routes).hasSize(lineDataSet.getFlightRoutes().size());

        assertThat(routes).extracting(Route::getId).containsOnlyElementsOf(getRouteIds(lineDataSet));

        Set<String> routeNames = lineDataSet.getFlightRoutes().stream()
                .map(FlightRoute::getRouteName)
                .collect(Collectors.toSet());
        assertThat(routes).extracting("name.value").containsOnlyElementsOf(routeNames);

        assertThat(routes).extracting("lineRef.value.ref").contains(line.getId());
    }

    private void assertValidJourneyPatterns(JourneyPatternsInFrame_RelStructure journeyPatternStruct, LineDataSet lineDataSet) {
        List<JAXBElement<?>> journeyPatternElements = journeyPatternStruct.getJourneyPattern_OrJourneyPatternView();

        List<JourneyPattern> journeyPatterns = journeyPatternElements.stream()
                .map(JAXBElement::getValue)
                .map(journeyPattern -> (JourneyPattern) journeyPattern)
                .collect(Collectors.toList());
        assertThat(journeyPatterns).hasSize(lineDataSet.getFlightRoutes().size());

        assertThat(journeyPatterns).extracting(JourneyPattern::getId).containsOnlyElementsOf(getJourneyPatternIds(lineDataSet));
        assertThat(journeyPatterns).extracting("routeRef.ref").containsOnlyElementsOf(getRouteIds(lineDataSet));

        for(JourneyPattern jp : journeyPatterns) {
        	StopPointInJourneyPattern stopPoint = (StopPointInJourneyPattern) jp.getPointsInSequence().getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern().get(0);
            assertThat(stopPoint).extracting("destinationDisplayRef.ref").isNotNull();
        	//assertThat(stopPoint).extracting("destinationDisplayRef.ref").containsOnlyElementsOf(getDestinationDisplayIds(lineDataSet));
            //assertThat(stopPoint).extracting("destinationDisplayRef.version").contains(VERSION_ONE);

        }


        // TODO add assertions for points in sequence
        /*
        journeyPatterns.forEach(journeyPattern -> assertThat(journeyPattern.getPointsInSequence()
                .getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()).hasSize(2));
        */
    }

    private Set<String> getRouteIds(LineDataSet lineDataSet) {
        return lineDataSet.getFlightRoutes().stream()
                .map(FlightRoute::getRouteDesignation)
                .map(designation -> Joiner.on(UNDERSCORE).join(lineDataSet.getAirlineIata(), designation))
                .map(objectId -> NetexObjectIdCreator.hashObjectId(objectId, 10))
                .map(hashedId -> String.format("AVI:Route:%s", hashedId))
                .collect(Collectors.toSet());
    }

    private Set<String> getJourneyPatternIds(LineDataSet lineDataSet) {
        return lineDataSet.getFlightRoutes().stream()
                .map(FlightRoute::getRouteDesignation)
                .map(designation -> Joiner.on(UNDERSCORE).join(lineDataSet.getAirlineIata(), designation))
                .map(objectId -> NetexObjectIdCreator.hashObjectId(objectId, 10))
                .map(hashedId -> String.format("AVI:JourneyPattern:%s", hashedId))
                .collect(Collectors.toSet());
    }

    private Set<String> getDestinationDisplayIds(LineDataSet lineDataSet) {
        return lineDataSet.getFlightRoutes().stream()
                .map(FlightRoute::getRouteDesignation)
                .map(designation -> Joiner.on(UNDERSCORE).join(lineDataSet.getAirlineIata(), designation))
                .map(objectId -> NetexObjectIdCreator.hashObjectId(objectId, 10))
                .map(hashedId -> String.format("AVI:DestinationDisplay:%s", hashedId))
                .collect(Collectors.toSet());
    }

}