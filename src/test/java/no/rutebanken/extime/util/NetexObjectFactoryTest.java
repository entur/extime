package no.rutebanken.extime.util;

import no.rutebanken.extime.config.CamelRouteDisabler;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rutebanken.netex.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.math.BigInteger;

import static no.rutebanken.extime.Constants.VERSION_ONE;

@ActiveProfiles("test")
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {CamelRouteDisabler.class, NetexObjectFactory.class})
public class NetexObjectFactoryTest {

    @Autowired
    private NetexObjectFactory netexObjectFactory;

    @Test
    public void createPointOnRoute() throws Exception {
        PointOnRoute pointOnRoute = netexObjectFactory.createPointOnRoute("59963891", "AVI:ScheduledStopPoint:17733643");

        Assertions.assertThat(pointOnRoute)
                .isNotNull()
                .isInstanceOf(PointOnRoute.class);

        Assertions.assertThat(pointOnRoute.getId())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:PointOnRoute:59963891");

        Assertions.assertThat(pointOnRoute.getPointRef().getValue().getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:ScheduledStopPoint:17733643");
    }

    @Test
    public void createStopPointInJourneyPattern() throws Exception {
        StopPointInJourneyPattern stopPointInJourneyPattern = netexObjectFactory.createStopPointInJourneyPattern(
                "99999991",
                BigInteger.ONE,
                "AVI:ScheduledStopPoint:77777771"
        );

        Assertions.assertThat(stopPointInJourneyPattern)
                .isNotNull()
                .isInstanceOf(StopPointInJourneyPattern.class);

        Assertions.assertThat(stopPointInJourneyPattern.getId())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:StopPointInJourneyPattern:99999991");

        Assertions.assertThat(stopPointInJourneyPattern.getOrder())
                .isNotNull()
                .isEqualTo(BigInteger.ONE);

        Assertions.assertThat(stopPointInJourneyPattern.getScheduledStopPointRef().getValue().getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:ScheduledStopPoint:77777771");
    }

    @Test
    public void createMultilingualString() throws Exception {
        MultilingualString multilingualString = netexObjectFactory.createMultilingualString("TEST");

        Assertions.assertThat(multilingualString)
                .isNotNull()
                .isInstanceOf(MultilingualString.class);

        Assertions.assertThat(multilingualString.getValue())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("TEST");
    }

    @Test
    public void createTimetabledPassingTime() throws Exception {
        TimetabledPassingTime timetabledPassingTime = netexObjectFactory.createTimetabledPassingTime(
                "AVI:StopPointInJourneyPattern:14398341");

        Assertions.assertThat(timetabledPassingTime)
                .isNotNull()
                .isInstanceOf(TimetabledPassingTime.class);

        Assertions.assertThat(timetabledPassingTime.getPointInJourneyPatternRef().getValue().getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:StopPointInJourneyPattern:14398341");
    }

    // reference structures testing

    @Test
    public void createOperatorRefStructure() throws Exception {
        // TODO also test the case where validation for references is disabled
        OperatorRefStructure operatorRefStructure = netexObjectFactory.createOperatorRefStructure("AVI:Operator:WF", Boolean.TRUE);

        Assertions.assertThat(operatorRefStructure)
                .isNotNull()
                .isInstanceOf(OperatorRefStructure.class);

        Assertions.assertThat(operatorRefStructure.getVersion())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo(VERSION_ONE);

        Assertions.assertThat(operatorRefStructure.getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:Operator:WF");
    }

    @Test
    public void createRouteRefStructure() throws Exception {
        RouteRefStructure routeRefStructure = netexObjectFactory.createRouteRefStructure("AVI:Route:WF716");

        Assertions.assertThat(routeRefStructure)
                .isNotNull()
                .isInstanceOf(RouteRefStructure.class);

        Assertions.assertThat(routeRefStructure.getVersion())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo(VERSION_ONE);

        Assertions.assertThat(routeRefStructure.getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:Route:WF716");
    }

    @Test
    public void createStopPlaceRefStructure() throws Exception {
        // TODO also test the case where validation for references is disabled
        StopPlaceRefStructure stopPlaceRefStructure = netexObjectFactory.createStopPlaceRefStructure("AVI:StopPlace:TRD", Boolean.TRUE);

        Assertions.assertThat(stopPlaceRefStructure)
                .isNotNull()
                .isInstanceOf(StopPlaceRefStructure.class);

        Assertions.assertThat(stopPlaceRefStructure.getVersion())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo(VERSION_ONE);

        Assertions.assertThat(stopPlaceRefStructure.getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:StopPlace:TRD");
    }

    @Test
    public void createScheduledStopPointRefStructure() throws Exception {
        // TODO also test the case where validation for references is disabled
        ScheduledStopPointRefStructure scheduledStopPointRefStructure = netexObjectFactory
                .createScheduledStopPointRefStructure("AVI:ScheduledStopPoint:77777771", Boolean.TRUE);

        Assertions.assertThat(scheduledStopPointRefStructure)
                .isNotNull()
                .isInstanceOf(ScheduledStopPointRefStructure.class);

        Assertions.assertThat(scheduledStopPointRefStructure.getVersion())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo(VERSION_ONE);

        Assertions.assertThat(scheduledStopPointRefStructure.getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:ScheduledStopPoint:77777771");
    }

    @Test
    public void createStopPointInJourneyPatternRefStructure() throws Exception {
        StopPointInJourneyPatternRefStructure stopPointInJourneyPatternRefStructure =
                netexObjectFactory.createStopPointInJourneyPatternRefStructure("AVI:StopPointInJourneyPattern:14398341");

        Assertions.assertThat(stopPointInJourneyPatternRefStructure)
                .isNotNull()
                .isInstanceOf(StopPointInJourneyPatternRefStructure.class);

        Assertions.assertThat(stopPointInJourneyPatternRefStructure.getVersion())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo(VERSION_ONE);

        Assertions.assertThat(stopPointInJourneyPatternRefStructure.getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:StopPointInJourneyPattern:14398341");
    }

    @Test
    public void createPointRefStructure() throws Exception {
        // TODO also test the case where validation for references is disabled
        PointRefStructure pointRefStructure = netexObjectFactory.createPointRefStructure("AVI:ScheduledStopPoint:77777771", Boolean.TRUE);

        Assertions.assertThat(pointRefStructure)
                .isNotNull()
                .isInstanceOf(PointRefStructure.class);

        Assertions.assertThat(pointRefStructure.getVersion())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo(VERSION_ONE);

        Assertions.assertThat(pointRefStructure.getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:ScheduledStopPoint:77777771");
    }

    @Test
    public void createRoutePointRefStructure() throws Exception {
        RoutePointRefStructure routePointRefStructure = netexObjectFactory.createRoutePointRefStructure("AVI:ScheduledStopPoint:77777771");

        Assertions.assertThat(routePointRefStructure)
                .isNotNull()
                .isInstanceOf(RoutePointRefStructure.class);

        Assertions.assertThat(routePointRefStructure.getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:ScheduledStopPoint:77777771");
    }

    @Test
    public void createDayTypeRefStructure() throws Exception {
        DayTypeRefStructure dayTypeRefStructure = netexObjectFactory.createDayTypeRefStructure("WF716:DayType:weekday");

        Assertions.assertThat(dayTypeRefStructure)
                .isNotNull()
                .isInstanceOf(DayTypeRefStructure.class);

        Assertions.assertThat(dayTypeRefStructure.getVersion())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo(VERSION_ONE);

        Assertions.assertThat(dayTypeRefStructure.getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("WF716:DayType:weekday");
    }

}