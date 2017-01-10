package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.AppTest;
import no.rutebanken.extime.model.ScheduledFlight;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rutebanken.netex.model.*;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {AppTest.class})
@Ignore
public class ScheduledFlightToNetexConverterTest {

    private ScheduledFlightToNetexConverter clazzUnderTest;

    @Before
    public void setUp() throws Exception {
        clazzUnderTest = new ScheduledFlightToNetexConverter();
    }

    @Test
    public void testCreateJourneyPattern() {
        String flightId = "WF305";
        String routePath = "Trondheim - Sandefjord";
        Route route = createDummyRoute(flightId, routePath);
        List<ScheduledStopPoint> scheduledStopPoints = createDummyScheduledStopPoints(flightId, "Trondheim", "Sandefjord");

        //JourneyPattern journeyPattern = clazzUnderTest.createJourneyPattern(flightId, route, scheduledStopPoints);
        JourneyPattern journeyPattern = null;

        Assertions.assertThat(journeyPattern)
                .isNotNull();
    }

    public ScheduledFlight createScheduledDirectFlight(String airlineIATA, String flightId) {
        ScheduledFlight directFlight = new ScheduledFlight();
        directFlight.setFlightId(BigInteger.ONE);
        directFlight.setAirlineIATA(airlineIATA);
        directFlight.setAirlineFlightId(flightId);
        directFlight.setDateOfOperation(LocalDate.now());
        directFlight.setDepartureAirportIATA("BGO");
        directFlight.setTimeOfDeparture(OffsetTime.MIN);
        directFlight.setArrivalAirportIATA("OSL");
        directFlight.setTimeOfArrival(OffsetTime.MAX);
        return directFlight;
    }

    public Direction createDummyDirection(String flightId) {
        ObjectFactory objectFactory = new ObjectFactory();
        return objectFactory.createDirection()
                .withVersion("any")
                .withId(String.format("avinor:dir:%s10001", flightId))
                .withName(createDummyMultilingualString("Outbound"))
                .withDirectionType(DirectionTypeEnumeration.OUTBOUND);
    }

    public Route createDummyRoute(String flightId, String routePath) {
        ObjectFactory objectFactory = new ObjectFactory();
        PointsOnRoute_RelStructure pointsOnRoute = objectFactory.createPointsOnRoute_RelStructure();
        RoutePointRefStructure routePointReference = objectFactory.createRoutePointRefStructure().withRef("routepoint:dummyid");
        PointOnRoute pointOnRoute = objectFactory.createPointOnRoute()
                .withVersion("any")
                .withId(String.format("avinor:por:%s10001", flightId))
                .withPointRef(objectFactory.createRoutePointRef(routePointReference));
        pointsOnRoute.getPointOnRoute().add(pointOnRoute);
        DirectionRefStructure directionRefStructure = objectFactory.createDirectionRefStructure()
                .withRef(createDummyDirection(flightId).getId());
        return objectFactory.createRoute()
                .withVersion("1")
                .withId(String.format("avinor:route:%s10001", flightId))
                .withName(createDummyMultilingualString(String.format("%s: %s", flightId, routePath)))
                .withPointsInSequence(pointsOnRoute)
                .withDirectionRef(directionRefStructure);
    }

    private List<ScheduledStopPoint> createDummyScheduledStopPoints(String flightId, String departureAirport, String arrivalAirport) {
        ObjectFactory objectFactory = new ObjectFactory();
        ScheduledStopPoint scheduledDepartureStopPoint = objectFactory.createScheduledStopPoint()
                .withVersion("1")
                .withId(String.format("avinor:sp:%s10001", flightId))
                .withName(createDummyMultilingualString(departureAirport));
        ScheduledStopPoint scheduledArrivalStopPoint = objectFactory.createScheduledStopPoint()
                .withVersion("1")
                .withId(String.format("avinor:sp:%s10002", flightId))
                .withName(createDummyMultilingualString(arrivalAirport));
        return Lists.newArrayList(scheduledDepartureStopPoint, scheduledArrivalStopPoint);
    }

    public MultilingualString createDummyMultilingualString(String value) {
        ObjectFactory objectFactory = new ObjectFactory();
        return objectFactory.createMultilingualString().withValue(value);
    }

}