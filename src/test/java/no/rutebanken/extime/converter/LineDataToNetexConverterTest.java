package no.rutebanken.extime.converter;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import no.rutebanken.extime.Constants;
import no.rutebanken.extime.ExtimeRouteBuilderIntegrationTestBase;
import no.rutebanken.extime.fixtures.LineDataSetFixture;
import no.rutebanken.extime.model.FlightRoute;
import no.rutebanken.extime.model.LineDataSet;
import no.rutebanken.extime.util.NetexObjectIdCreator;
import no.rutebanken.extime.util.NetexObjectIdTypes;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.AllVehicleModesOfTransportEnumeration;
import org.rutebanken.netex.model.Common_VersionFrameStructure;
import org.rutebanken.netex.model.DestinationDisplay;
import org.rutebanken.netex.model.JourneyPattern;
import org.rutebanken.netex.model.JourneyPatternsInFrame_RelStructure;
import org.rutebanken.netex.model.Line;
import org.rutebanken.netex.model.LinkSequence_VersionStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.Route;
import org.rutebanken.netex.model.RouteRefStructure;
import org.rutebanken.netex.model.RoutesInFrame_RelStructure;
import org.rutebanken.netex.model.ServiceCalendarFrame;
import org.rutebanken.netex.model.ServiceFrame;
import org.rutebanken.netex.model.StopPointInJourneyPattern;
import org.rutebanken.netex.model.TimetableFrame;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.xml.bind.JAXBElement;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static no.rutebanken.extime.Constants.NETEX_PROFILE_VERSION;
import static no.rutebanken.extime.Constants.UNDERSCORE;
import static no.rutebanken.extime.Constants.VERSION_ONE;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class LineDataToNetexConverterTest extends ExtimeRouteBuilderIntegrationTestBase {

    @Autowired
    private LineDataToNetexConverter netexConverter;

    @Test
    void verifyFrameAttributes() {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-BGO", 1), Pair.of("BGO-OSL", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("DY", "OSL-BGO", routeJourneyPairs);
        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();

        NetexTestUtils.verifyCompositeFrameAttributes(publicationDelivery);
        NetexTestUtils.verifyServiceFrameAttributes(publicationDelivery);

        TimetableFrame timetableFrame = NetexTestUtils.getFrames(TimetableFrame.class, NetexTestUtils.getDataObjectFrames(publicationDelivery)).getFirst();
        assertThat(timetableFrame).hasFieldOrPropertyWithValue("version", VERSION_ONE);
        assertThat(timetableFrame.getId()).matches(id -> id.split(":")[1].equals(NetexObjectIdTypes.TIMETABLE_FRAME_KEY), "TimetableFrame");

        ServiceCalendarFrame serviceCalendarFrame = NetexTestUtils.getFrames(ServiceCalendarFrame.class, NetexTestUtils.getDataObjectFrames(publicationDelivery)).getFirst();
        assertThat(serviceCalendarFrame).hasFieldOrPropertyWithValue("version", VERSION_ONE);
        assertThat(serviceCalendarFrame.getId()).matches(id -> id.split(":")[1].equals(NetexObjectIdTypes.SERVICE_CALENDAR_FRAME_KEY), "ServiceCalendarFrame");
    }

    @Test
    void testLineWithRoundTripRoutes() {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-BGO", 1), Pair.of("BGO-OSL", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("DY", "OSL-BGO", routeJourneyPairs);

        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = NetexTestUtils.getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = NetexTestUtils.getFrames(ServiceFrame.class, dataObjectFrames).getFirst();

        Line line = (Line) serviceFrame.getLines().getLine_().getFirst().getValue();

        assertValidLine(line, lineDataSet);
        assertValidRoutes(serviceFrame.getRoutes(), lineDataSet, line);
    }

    @Test
    void testLineWithStopoverRoutes() {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("TRD-OSL-BGO-MOL-SOG", 1), Pair.of("SOG-MOL-BGO-OSL-TRD", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("WF", "TRD-SOG", routeJourneyPairs);

        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = NetexTestUtils.getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = NetexTestUtils.getFrames(ServiceFrame.class, dataObjectFrames).getFirst();

        Line line = (Line) serviceFrame.getLines().getLine_().getFirst().getValue();

        assertValidLine(line, lineDataSet);
        assertValidRoutes(serviceFrame.getRoutes(), lineDataSet, line);
    }

    @Test
    void testDestinationDisplaysNoVias() {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-BGO", 1), Pair.of("BGO-OSL", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("DY", "OSL-BGO", routeJourneyPairs);

        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = NetexTestUtils.getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = NetexTestUtils.getFrames(ServiceFrame.class, dataObjectFrames).getFirst();

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
    void testDestinationDisplaysWithVias() {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-SOG-BGO", 1), Pair.of("BGO-SOG-OSL", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("DY", "OSL-BGO", routeJourneyPairs);

        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = NetexTestUtils.getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = NetexTestUtils.getFrames(ServiceFrame.class, dataObjectFrames).getFirst();

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
                assertThat(destinationDisplay.getVias().getVia().getFirst().getDestinationDisplayRef().getVersion()).isEqualTo(VERSION_ONE);
                assertThat(destinationDisplay.getVias().getVia().getFirst().getDestinationDisplayRef().getRef()).isEqualTo("AVI:DestinationDisplay:DYOSLBGO-SOG");
            }
            if (destinationDisplay.getId().equals("AVI:DestinationDisplay:DY_BGO-SOG-OSL")) {
                assertThat(destinationDisplay.getFrontText().getValue()).isEqualTo("Oslo");
                assertThat(destinationDisplay.getVias().getVia()).hasSize(1);
                assertThat(destinationDisplay.getVias().getVia().getFirst().getDestinationDisplayRef().getVersion()).isEqualTo(VERSION_ONE);
                assertThat(destinationDisplay.getVias().getVia().getFirst().getDestinationDisplayRef().getRef()).isEqualTo("AVI:DestinationDisplay:DYOSLBGO-SOG");
            }
        }
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

    private void assertValidLine(Line line, LineDataSet lineDataSet) {
        assertThat(line.getId()).isEqualTo(String.format("AVI:Line:%s_%s", lineDataSet.getAirlineIata(), lineDataSet.getLineDesignation()));
        assertThat(line.getName().getValue()).isEqualTo(lineDataSet.getLineName());
        assertThat(line.getTransportMode()).isEqualTo(AllVehicleModesOfTransportEnumeration.AIR);
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
                .toList();
        assertThat(routes).hasSize(lineDataSet.getFlightRoutes().size());

        assertThat(routes).extracting(Route::getId).containsAll(getRouteIds(lineDataSet));

        Set<String> routeNames = lineDataSet.getFlightRoutes().stream()
                .map(FlightRoute::getRouteName)
                .collect(Collectors.toSet());
        assertThat(routes).extracting("name.value").containsAll(routeNames);

        assertThat(routes).extracting("lineRef.value.ref").contains(line.getId());
    }

    private void assertValidJourneyPatterns(JourneyPatternsInFrame_RelStructure journeyPatternStruct, LineDataSet lineDataSet) {
        List<JAXBElement<?>> journeyPatternElements = journeyPatternStruct.getJourneyPattern_OrJourneyPatternView();

        List<JourneyPattern> journeyPatterns = journeyPatternElements.stream()
                .map(JAXBElement::getValue)
                .map(journeyPattern -> (JourneyPattern) journeyPattern)
                .toList();
        assertThat(journeyPatterns).hasSize(lineDataSet.getFlightRoutes().size());

        assertThat(journeyPatterns).extracting(JourneyPattern::getId).containsAll(getJourneyPatternIds(lineDataSet));
        assertThat(journeyPatterns).extracting("routeRef.ref").containsAll(getRouteIds(lineDataSet));

        for(JourneyPattern jp : journeyPatterns) {
        	StopPointInJourneyPattern stopPoint = (StopPointInJourneyPattern) jp.getPointsInSequence().getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern().getFirst();
            assertThat(stopPoint).extracting("destinationDisplayRef.ref").isNotNull();
        }
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

}