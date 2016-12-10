package no.rutebanken.extime.util;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.rutebanken.netex.model.*;

import java.math.BigInteger;

import static no.rutebanken.extime.Constants.VERSION_ONE;

public class NetexObjectFactoryTest {

    @Test
    public void createStopPlace() throws Exception {
    }

    @Test
    public void createPointOnRoute() throws Exception {
        PointOnRoute pointOnRoute = NetexObjectFactory.createPointOnRoute("59963891", "AVI:ScheduledStopPoint:17733643");

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
        StopPointInJourneyPattern stopPointInJourneyPattern = NetexObjectFactory.createStopPointInJourneyPattern(
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
        MultilingualString multilingualString = NetexObjectFactory.createMultilingualString("TEST");

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
        TimetabledPassingTime timetabledPassingTime = NetexObjectFactory.createTimetabledPassingTime(
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
        OperatorRefStructure operatorRefStructure = NetexObjectFactory.createOperatorRefStructure("AVI:Operator:WF");

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
        RouteRefStructure routeRefStructure = NetexObjectFactory.createRouteRefStructure("AVI:Route:WF716");

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
        StopPlaceRefStructure stopPlaceRefStructure = NetexObjectFactory.createStopPlaceRefStructure("AVI:StopPlace:TRD");

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
        ScheduledStopPointRefStructure scheduledStopPointRefStructure = NetexObjectFactory
                .createScheduledStopPointRefStructure("AVI:ScheduledStopPoint:77777771");

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
                NetexObjectFactory.createStopPointInJourneyPatternRefStructure("AVI:StopPointInJourneyPattern:14398341");

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
        PointRefStructure pointRefStructure = NetexObjectFactory.createPointRefStructure("AVI:ScheduledStopPoint:77777771");

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
        RoutePointRefStructure routePointRefStructure = NetexObjectFactory.createRoutePointRefStructure("AVI:ScheduledStopPoint:77777771");

        Assertions.assertThat(routePointRefStructure)
                .isNotNull()
                .isInstanceOf(RoutePointRefStructure.class);

        Assertions.assertThat(routePointRefStructure.getVersion())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo(VERSION_ONE);

        Assertions.assertThat(routePointRefStructure.getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:ScheduledStopPoint:77777771");
    }

    @Test
    public void createDayTypeRefStructure() throws Exception {
        DayTypeRefStructure dayTypeRefStructure = NetexObjectFactory.createDayTypeRefStructure("WF716:DayType:weekday");

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