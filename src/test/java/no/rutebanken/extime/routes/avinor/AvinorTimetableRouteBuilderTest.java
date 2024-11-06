package no.rutebanken.extime.routes.avinor;

import com.google.common.collect.Lists;
import no.rutebanken.extime.model.*;
import no.rutebanken.extime.ExtimeCamelRouteBuilderIntegrationTestBase;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.xml.bind.JAXBElement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static no.rutebanken.extime.TestUtils.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {AvinorTimetableRouteBuilder.class} , properties = {
        "avinor.timetable.scheduler.consumer=direct:start",
        "avinor.timetable.period.forward=14",
        "avinor.timetable.feed.endpoint=mock:timetableFeedEndpoint",
        "avinor.airport.feed.endpoint=mock:airportFeedEndpoint",
        "avinor.airports.small=EVE,KRS,MOL,SOG,TOS",
        "avinor.airports.large=BGO,BOO,SVG,TRD",
        "avinor.airline.feed.endpoint=mock:airlineFeedEndpoint",
        "netex.generated.output.path=target/netex-mock",
        "netex.compressed.output.path=target/marduk-mock",
        "queue.upload.destination.name=MockMardukQueue",
        "avinor.timetable.dump.output.path=target/flights"
} )
class AvinorTimetableRouteBuilderTest extends ExtimeCamelRouteBuilderIntegrationTestBase {


    @EndpointInject("mock:fetchTimetable")
    protected MockEndpoint mockFetchTimetable;

    @EndpointInject("mock:convertToScheduledFlights")
    protected MockEndpoint mockConvertToScheduledFlights;

    @EndpointInject("mock:direct:convertScheduledFlightsToNetex")
    protected MockEndpoint mockConvertScheduledFlightsToNetex;

    @EndpointInject("mock:airlineIataPreProcess")
    protected MockEndpoint mockAirlineIataPreProcess;

    @EndpointInject("mock:direct:retrieveAirlineNameResource")
    protected MockEndpoint mockRetrieveAirlineNameResource;

    @EndpointInject("mock:direct:enrichScheduledFlightWithAirportNames")
    protected MockEndpoint mockEnrichScheduledFlightWithAirportNames;

    @EndpointInject("mock:convertToNetex")
    protected MockEndpoint mockConvertToNetex;

    @EndpointInject("mock:generateFileName")
    protected MockEndpoint mockGenerateFileName;

    @EndpointInject("mock:file:target/netex")
    protected MockEndpoint mockFileTargetNetex;

    @Produce("direct:convertScheduledFlightsToNetex")
    protected ProducerTemplate convertScheduledFlightsToNetexTemplate;

    @Test
    @Disabled
    void testTimetableScheduler() throws Exception {

        AdviceWith.adviceWith(context, "AvinorTimetableSchedulerStarter", a -> {
            a.weaveById("TimetableAirportIATAProcessor").replace().to("mock:setupIataCodes");
            a.interceptSendToEndpoint("mock:setupIataCodes").process(exchange -> exchange.getIn().setBody(new AirportIATA[]{AirportIATA.OSL, AirportIATA.BGO, AirportIATA.EVE}));
            a.weaveById("TimetableDateRangeProcessor").replace().to("mock:setupDateRanges");
            a.mockEndpointsAndSkip("direct:fetchAndCacheAirportName");
            a.weaveById("FetchTimetableProcessor").replace().to("mock:fetchTimetable");
            a.interceptSendToEndpoint("mock:fetchTimetable").process(exchange -> exchange.getIn().setBody(createDummyFlights()));
            a.weaveById("ConvertToLineDataSetsBeanProcessor").replace().to("mock:convertToScheduledFlights");
            a.mockEndpointsAndSkip("direct:convertScheduledFlightsToNetex");
        });

        context.start();

        mockFetchTimetable.expectedMessageCount(3);
        mockConvertToScheduledFlights.expectedMessageCount(1);
        mockConvertScheduledFlightsToNetex.expectedMessageCount(1);

        startTemplate.sendBody(null);

        mockFetchTimetable.assertIsSatisfied();
        mockConvertToScheduledFlights.assertIsSatisfied();
        mockConvertScheduledFlightsToNetex.assertIsSatisfied();

    }


    @Test
    @Disabled
    void testConvertScheduledFlightsToNetex() throws Exception {

        AdviceWith.adviceWith(context, "LineDataSetsToNetexConverter", a -> {
                    a.weaveById("AirlineIataPreEnrichProcessor").replace().to("mock:airlineIataPreProcess");
                    a.weaveById("ConvertLineDataSetsToNetexProcessor").replace().to("mock:convertToNetex");
                    a.interceptSendToEndpoint("mock:convertToNetex").process(exchange -> exchange.getIn().setBody(createPublicationDeliveryElement()));
                    a.weaveById("GenerateFileNameProcessor").replace().to("mock:generateFileName");
                    a.interceptSendToEndpoint("mock:generateFileName").process(
                            exchange -> exchange.getIn().setHeader("FileNameGenerated", "067e6162-3b6f-4ae2-a171-2470b63dff00"));
                    a.mockEndpointsAndSkip(
                            "direct:retrieveAirlineNameResource",
                            "direct:enrichScheduledFlightWithAirportNames",
                            "file:target/netex"
                    );
                });

        context.start();

        mockAirlineIataPreProcess.expectedMessageCount(2);
        mockRetrieveAirlineNameResource.expectedMessageCount(2);
        mockEnrichScheduledFlightWithAirportNames.expectedMessageCount(2);
        mockConvertToNetex.expectedMessageCount(2);
        mockGenerateFileName.expectedMessageCount(2);

        mockFileTargetNetex.expectedMessageCount(2);
        mockFileTargetNetex.expectedHeaderReceived(Exchange.FILE_NAME, "067e6162-3b6f-4ae2-a171-2470b63dff00.xml");
        mockFileTargetNetex.expectedBodiesReceived(createPublicationDelivery(), createPublicationDelivery());

        List<ScheduledFlight> scheduledFlights = Lists.newArrayList(
                createScheduledFlight("WF", "WF739", LocalDate.now()),
                createScheduledFlight("SK", "SK1038", LocalDate.now())
        );

        convertScheduledFlightsToNetexTemplate.sendBody(scheduledFlights);

        mockAirlineIataPreProcess.assertIsSatisfied();
        mockRetrieveAirlineNameResource.assertIsSatisfied();
        mockEnrichScheduledFlightWithAirportNames.assertIsSatisfied();
        mockConvertToNetex.assertIsSatisfied();
        mockGenerateFileName.assertIsSatisfied();
        mockFileTargetNetex.assertIsSatisfied();

    }

    private List<FlightLeg> createDummyFlights() {
        return Lists.newArrayList(
                createFlight(1L, "SK", "4455", "BGO", ZDT_2017_01_01_00_00, "OSL", ZDT_2017_01_01_23_59),
                createFlight(2L, "DY", "6677", "BGO", ZDT_2017_01_02_00_00, "TRD", ZDT_2017_01_02_23_59),
                createFlight(3L, "WF", "199", "BGO", ZDT_2017_01_03_00_00, "SVG", ZDT_2017_01_03_23_59)
        );
    }

    private ScheduledFlight createScheduledFlight(String airlineIATA, String airlineFlightId, LocalDate dateOfOperation) {
        ScheduledFlight scheduledFlight = new ScheduledFlight();
        scheduledFlight.setAirlineIATA(airlineIATA);
        scheduledFlight.setAirlineFlightId(airlineFlightId);
        scheduledFlight.setDateOfOperation(dateOfOperation);
        scheduledFlight.setDepartureAirportIATA("");
        scheduledFlight.setArrivalAirportIATA("");
        scheduledFlight.setArrivalAirportName("");
        scheduledFlight.setTimeOfDeparture(LocalTime.of(0, 0));
        scheduledFlight.setTimeOfArrival(LocalTime.of(23, 59));
        return scheduledFlight;
    }


    private JAXBElement<PublicationDeliveryStructure> createPublicationDeliveryElement() {
        ObjectFactory objectFactory = new ObjectFactory();
        PublicationDeliveryStructure publicationDeliveryStructure1 = objectFactory.createPublicationDeliveryStructure()
                .withVersion("1.0")
                .withPublicationTimestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2016-08-16T08:24:21Z", LocalDateTime::from))
                .withParticipantRef("AVI");
        return objectFactory.createPublicationDelivery(publicationDeliveryStructure1);
    }

    private PublicationDeliveryStructure createPublicationDelivery() {
        ObjectFactory objectFactory = new ObjectFactory();
        return objectFactory.createPublicationDeliveryStructure()
                .withVersion("1.0")
                .withPublicationTimestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2016-08-16T08:24:21Z", LocalDateTime::from))
                .withParticipantRef("AVI");
    }

}