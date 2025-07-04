package no.rutebanken.extime.routes.avinor;

import no.rutebanken.extime.ExtimeRouteBuilderIntegrationTestBase;
import no.rutebanken.extime.model.AirlineIATA;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.FlightEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.HashMap;
import java.util.List;

import static no.rutebanken.extime.TestUtils.*;
import static no.rutebanken.extime.converter.CommonDataToNetexConverter.PROPERTY_NSR_QUAY_MAP;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {AvinorTimetableRouteBuilder.class}, properties = {
        "avinor.timetable.scheduler.consumer=direct:start",
        "avinor.timetable.period.forward=14",
        "avinor.timetable.feed.endpoint=mock:timetableFeedEndpoint",
        "avinor.airports.small=EVE,KRS,MOL,SOG,TOS",
        "avinor.airports.large=BGO,BOO,SVG,TRD",
        "netex.generated.output.path=target/netex-mock",
        "netex.compressed.output.path=target/marduk-mock",
        "queue.upload.destination.name=MockMardukQueue",
        "avinor.timetable.dump.output.path=target/flights"
})
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AvinorTimetableRouteBuilderTest extends ExtimeRouteBuilderIntegrationTestBase {

    @EndpointInject("mock:notifyMarduk")
    protected MockEndpoint mockNotifyMarduk;



    @Test
    void testTimetableScheduler() throws Exception {

        AdviceWith.adviceWith(context, "AvinorTimetableSchedulerStarter", a -> {
            a.weaveByToUri("direct:refreshStops").replace().process(
                    exchange -> exchange.setProperty(PROPERTY_NSR_QUAY_MAP, new HashMap<>())
            );

            a.weaveByToUri("direct:fetchFlights").replace().process(
                    exchange -> exchange.getIn().setBody(createDummyFlightEvents())
            );
        });

        AdviceWith.adviceWith(context, "CompressNetexAndSendToStorage", a ->
            a.weaveByToUri("direct:notifyMarduk").replace().to("mock:notifyMarduk")
        );

        mockNotifyMarduk.expectedMessageCount(1);
        context.start();
        startTemplate.sendBody(null);
        mockNotifyMarduk.assertIsSatisfied();

    }

    private static List<FlightEvent> createDummyFlightEvents() {
        return List.of(
                new FlightEvent(1L, "DY1", AirlineIATA.DY, AirportIATA.OSL, AirportIATA.BGO, ZDT_2017_01_01, LT_00_00, LT_01_00),
                new FlightEvent(1L, "DY1", AirlineIATA.DY, AirportIATA.BGO, AirportIATA.TRD, ZDT_2017_01_01, LT_00_00, LT_01_00)
        );

    }


}