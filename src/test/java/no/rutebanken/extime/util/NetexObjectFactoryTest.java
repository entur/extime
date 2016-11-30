package no.rutebanken.extime.util;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.rutebanken.netex.model.ScheduledStopPointRefStructure;
import org.rutebanken.netex.model.StopPointInJourneyPattern;

import java.math.BigInteger;

import static org.junit.Assert.*;

public class NetexObjectFactoryTest {

    @Test
    public void createStopPlace() throws Exception {
    }

    @Test
    public void createPointOnRoute() throws Exception {

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
    public void createScheduledStopPointRefStructure() throws Exception {
        ScheduledStopPointRefStructure scheduledStopPointRefStructure = NetexObjectFactory
                .createScheduledStopPointRefStructure("AVI:ScheduledStopPoint:77777771");

        Assertions.assertThat(scheduledStopPointRefStructure)
                .isNotNull()
                .isInstanceOf(ScheduledStopPointRefStructure.class);

        Assertions.assertThat(scheduledStopPointRefStructure.getRef())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:ScheduledStopPoint:77777771");
    }

    @Test
    public void createMultilingualString() throws Exception {

    }

}