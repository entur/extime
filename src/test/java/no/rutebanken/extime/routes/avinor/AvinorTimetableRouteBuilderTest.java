package no.rutebanken.extime.routes.avinor;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import no.rutebanken.extime.ExtimeCamelRouteBuilderIntegrationTestBase;
import no.rutebanken.extime.model.AirlineIATA;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.FlightEvent;
import no.rutebanken.extime.model.StopVisitType;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;

import static no.rutebanken.extime.TestUtils.ZDT_2017_01_01_00_00;
import static no.rutebanken.extime.TestUtils.ZDT_2017_01_01_23_59;
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
class AvinorTimetableRouteBuilderTest extends ExtimeCamelRouteBuilderIntegrationTestBase {

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


    private List<FlightEvent> createDummyFlightEvents() {
        return List.of(
                new FlightEvent(StopVisitType.DEPARTURE, 1L, "DY1", AirlineIATA.DY, AirportIATA.OSL, ZDT_2017_01_01_00_00),
                new FlightEvent(StopVisitType.ARRIVAL, 1L, "DY1", AirlineIATA.DY, AirportIATA.BGO, ZDT_2017_01_01_23_59)
        );

    }


}