package no.rutebanken.extime.util;

import no.rutebanken.extime.ExtimeRouteBuilderIntegrationTestBase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.DayTypeRefStructure;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.OperatorRefStructure;
import org.rutebanken.netex.model.PointOnRoute;
import org.rutebanken.netex.model.PointRefStructure;
import org.rutebanken.netex.model.RoutePointRefStructure;
import org.rutebanken.netex.model.RouteRefStructure;
import org.rutebanken.netex.model.ScheduledStopPointRefStructure;
import org.rutebanken.netex.model.StopPlaceRefStructure;
import org.rutebanken.netex.model.StopPointInJourneyPattern;
import org.rutebanken.netex.model.StopPointInJourneyPatternRefStructure;
import org.rutebanken.netex.model.TimetabledPassingTime;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigInteger;

import static no.rutebanken.extime.Constants.VERSION_ONE;

public class NetexObjectFactoryTest extends ExtimeRouteBuilderIntegrationTestBase {

    @Autowired
    private NetexObjectFactory netexObjectFactory;

    @Test
    public void createPointOnRoute() {
        PointOnRoute pointOnRoute = netexObjectFactory.createPointOnRoute("59963891", "AVI:ScheduledStopPoint:17733643",7);

        Assertions.assertThat(pointOnRoute)
                .isNotNull()
                .isInstanceOf(PointOnRoute.class);

        Assertions.assertThat(pointOnRoute.getId())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:PointOnRoute:59963891");

        Assertions.assertThat(pointOnRoute.getOrder())
                .isNotNull()
                .isEqualTo(BigInteger.valueOf(7));

        Assertions.assertThat(pointOnRoute.getPointRef().getValue().getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:ScheduledStopPoint:17733643");
    }

    @Test
    public void createStopPointInJourneyPattern() {
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
    public void createMultilingualString() {
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
    public void createTimetabledPassingTime() {
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
    public void createOperatorRefStructure() {
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
    public void createRouteRefStructure() {
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
    public void createStopPlaceRefStructure() {
        // TODO also test the case where validation for references is disabled
        StopPlaceRefStructure stopPlaceRefStructure = netexObjectFactory.createStopPlaceRefStructure("AVI:StopPlace:TRD");

        Assertions.assertThat(stopPlaceRefStructure)
                .isNotNull()
                .isInstanceOf(StopPlaceRefStructure.class);

        Assertions.assertThat(stopPlaceRefStructure.getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:StopPlace:TRD");
    }

    @Test
    public void createScheduledStopPointRefStructure() {
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
    public void createStopPointInJourneyPatternRefStructure() {
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
    public void createPointRefStructure() {
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
    public void createRoutePointRefStructure() {
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
    public void createDayTypeRefStructure() {
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