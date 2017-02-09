package no.rutebanken.extime.converter;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import no.rutebanken.extime.config.CamelRouteDisabler;
import no.rutebanken.extime.fixtures.LineDataSetFixture;
import no.rutebanken.extime.model.FlightRoute;
import no.rutebanken.extime.model.LineDataSet;
import no.rutebanken.extime.util.NetexObjectIdCreator;
import no.rutebanken.extime.util.NetexObjectIdTypes;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rutebanken.netex.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.xml.bind.JAXBElement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static no.rutebanken.extime.Constants.*;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
@ActiveProfiles("test")
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {CamelRouteDisabler.class, LineDataToNetexConverter.class})
public class LineDataToNetexConverterTest {

    @Autowired
    private LineDataToNetexConverter netexConverter;

    @Test
    public void verifyFrameAttributes() throws Exception {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-BGO", 1), Pair.of("BGO-OSL", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("DY", "OSL-BGO", routeJourneyPairs);
        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();

        CompositeFrame compositeFrame = getFrames(CompositeFrame.class, publicationDelivery.getDataObjects().getCompositeFrameOrCommonFrame()).get(0);
        assertThat(compositeFrame).hasFieldOrPropertyWithValue("version", VERSION_ONE);
        assertThat(compositeFrame.getId()).matches(id -> id.split(":")[1].equals(NetexObjectIdTypes.COMPOSITE_FRAME_KEY), "CompositeFrame");

        ServiceFrame serviceFrame = getFrames(ServiceFrame.class, getDataObjectFrames(publicationDelivery)).get(0);
        assertThat(serviceFrame).hasFieldOrPropertyWithValue("version", VERSION_ONE);
        assertThat(serviceFrame.getId()).matches(id -> id.split(":")[1].equals(NetexObjectIdTypes.SERVICE_FRAME_KEY), "ServiceFrame");

        TimetableFrame timetableFrame = getFrames(TimetableFrame.class, getDataObjectFrames(publicationDelivery)).get(0);
        assertThat(timetableFrame).hasFieldOrPropertyWithValue("version", VERSION_ONE);
        assertThat(timetableFrame.getId()).matches(id -> id.split(":")[1].equals(NetexObjectIdTypes.TIMETABLE_FRAME_KEY), "TimetableFrame");

        ServiceCalendarFrame serviceCalendarFrame = getFrames(ServiceCalendarFrame.class, getDataObjectFrames(publicationDelivery)).get(0);
        assertThat(serviceCalendarFrame).hasFieldOrPropertyWithValue("version", VERSION_ONE);
        assertThat(serviceCalendarFrame.getId()).matches(id -> id.split(":")[1].equals(NetexObjectIdTypes.SERVICE_CALENDAR_FRAME_KEY), "ServiceCalendarFrame");
    }

    @Test
    public void testLineWithRoundTripRoutes() throws Exception {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-BGO", 1), Pair.of("BGO-OSL", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("DY", "OSL-BGO", routeJourneyPairs);

        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = getFrames(ServiceFrame.class, dataObjectFrames).get(0);

        Line line = (Line) serviceFrame.getLines().getLine_().get(0).getValue();

        assertValidNetwork(serviceFrame.getNetwork(), lineDataSet.getAirlineIata());
        assertValidLine(line, lineDataSet);
        assertValidRoutes(serviceFrame.getRoutes(), lineDataSet, line);
    }

    @Test
    public void testLineWithStopoverRoutes() throws Exception {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("TRD-OSL-BGO-MOL-SOG", 1), Pair.of("SOG-MOL-BGO-OSL-TRD", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("WF", "TRD-SOG", routeJourneyPairs);

        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = getFrames(ServiceFrame.class, dataObjectFrames).get(0);

        Line line = (Line) serviceFrame.getLines().getLine_().get(0).getValue();

        assertValidNetwork(serviceFrame.getNetwork(), lineDataSet.getAirlineIata());
        assertValidLine(line, lineDataSet);
        assertValidRoutes(serviceFrame.getRoutes(), lineDataSet, line);
    }

    @Test
    public void testDestinationDisplaysNoVias() throws Exception {
        List<Pair<String, Integer>> routeJourneyPairs = Lists.newArrayList(Pair.of("OSL-BGO", 1), Pair.of("BGO-OSL", 1));
        LineDataSet lineDataSet = LineDataSetFixture.createLineDataSet("DY", "OSL-BGO", routeJourneyPairs);

        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(lineDataSet).getValue();
        assertValidPublicationDelivery(publicationDelivery, lineDataSet.getLineName());

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = getFrames(ServiceFrame.class, dataObjectFrames).get(0);

        // check journey patterns for destination display reference
        JourneyPatternsInFrame_RelStructure journeyPatternStruct = serviceFrame.getJourneyPatterns();
        assertValidJourneyPatterns(journeyPatternStruct, lineDataSet);

        // check destination displays
        List<DestinationDisplay> destinationDisplays = serviceFrame.getDestinationDisplays().getDestinationDisplay();

        assertThat(destinationDisplays).extracting("version").contains(VERSION_ONE);

        assertThat(destinationDisplays)
                .hasSize(4)
                .extracting("id")
                .contains("AVI:DestinationDisplay:48329871", "AVI:DestinationDisplay:52328893",
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

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = getDataObjectFrames(publicationDelivery);
        ServiceFrame serviceFrame = getFrames(ServiceFrame.class, dataObjectFrames).get(0);

        // check journey patterns for destination display reference
        JourneyPatternsInFrame_RelStructure journeyPatternStruct = serviceFrame.getJourneyPatterns();
        assertValidJourneyPatterns(journeyPatternStruct, lineDataSet);

        // check destination displays
        List<DestinationDisplay> destinationDisplays = serviceFrame.getDestinationDisplays().getDestinationDisplay();

        assertThat(destinationDisplays).extracting("version").contains(VERSION_ONE);

        assertThat(destinationDisplays)
                .hasSize(5)
                .extracting("id")
                .contains("AVI:DestinationDisplay:16464597", "AVI:DestinationDisplay:46780243",
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

    private void assertValidPublicationDelivery(PublicationDeliveryStructure publicationDelivery, String lineName) {
        assertThat(publicationDelivery).isNotNull();
        assertThat(publicationDelivery.getVersion()).isEqualTo(NETEX_PROFILE_VERSION);
        assertThat(publicationDelivery.getPublicationTimestamp()).isNotNull().isBefore(OffsetDateTime.now());
        assertThat(publicationDelivery.getParticipantRef()).isEqualTo("Avinor");
        assertThat(publicationDelivery.getDescription()).isNotNull();
        assertThat(publicationDelivery.getDescription().getValue()).isEqualTo("Line: " + lineName);
        assertThat(publicationDelivery.getDataObjects()).isNotNull();
    }

    private void assertValidNetwork(Network network, String airlineIata) {
        assertThat(network).isNotNull();
        assertThat(network.getId()).isEqualTo(String.format("AVI:Network:%s", airlineIata));
        assertThat(network.getName().getValue()).isEqualTo(LineDataSetFixture.getAirlineName(airlineIata));
    }

    private void assertValidLine(Line line, LineDataSet lineDataSet) {
        assertThat(line.getId()).isEqualTo(String.format("AVI:Line:%s_%s", lineDataSet.getAirlineIata(), lineDataSet.getLineDesignation()));
        assertThat(line.getName().getValue()).isEqualTo(lineDataSet.getLineName());
        assertThat(line.getTransportMode()).isEqualTo(AllVehicleModesOfTransportEnumeration.AIR);
        assertThat(line.getPublicCode()).isEqualTo(lineDataSet.getLineDesignation());
        assertThat(line.getOperatorRef().getRef()).isEqualTo(String.format("AVI:Operator:%s", lineDataSet.getAirlineIata()));

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
        assertThat(journeyPatterns).extracting("destinationDisplayRef.ref").containsOnlyElementsOf(getDestinationDisplayIds(lineDataSet));
        assertThat(journeyPatterns).extracting("destinationDisplayRef.version").contains(VERSION_ONE);

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
                .map(objectId -> NetexObjectIdCreator.hashObjectId(objectId, 8))
                .map(hashedId -> String.format("AVI:Route:%s", hashedId))
                .collect(Collectors.toSet());
    }

    private Set<String> getJourneyPatternIds(LineDataSet lineDataSet) {
        return lineDataSet.getFlightRoutes().stream()
                .map(FlightRoute::getRouteDesignation)
                .map(designation -> Joiner.on(UNDERSCORE).join(lineDataSet.getAirlineIata(), designation))
                .map(objectId -> NetexObjectIdCreator.hashObjectId(objectId, 8))
                .map(hashedId -> String.format("AVI:JourneyPattern:%s", hashedId))
                .collect(Collectors.toSet());
    }

    private Set<String> getDestinationDisplayIds(LineDataSet lineDataSet) {
        return lineDataSet.getFlightRoutes().stream()
                .map(FlightRoute::getRouteDesignation)
                .map(designation -> Joiner.on(UNDERSCORE).join(lineDataSet.getAirlineIata(), designation))
                .map(objectId -> NetexObjectIdCreator.hashObjectId(objectId, 8))
                .map(hashedId -> String.format("AVI:DestinationDisplay:%s", hashedId))
                .collect(Collectors.toSet());
    }

    private List<JAXBElement<? extends Common_VersionFrameStructure>> getDataObjectFrames(PublicationDeliveryStructure publicationDelivery) {
        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = publicationDelivery.getDataObjects().getCompositeFrameOrCommonFrame();
        CompositeFrame compositeFrame = getFrames(CompositeFrame.class, dataObjectFrames).get(0);
        return compositeFrame.getFrames().getCommonFrame();
    }

    public <T> List<T> getFrames(Class<T> clazz, List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames) {
        List<T> foundFrames = new ArrayList<>();

        for (JAXBElement<? extends Common_VersionFrameStructure> frame : dataObjectFrames) {
            if (frame.getValue().getClass().equals(clazz)) {
                foundFrames.add(clazz.cast(frame.getValue()));
            }
        }

        return foundFrames;
    }

}