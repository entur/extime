package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.AppTest;
import no.rutebanken.extime.config.*;
import no.rutebanken.extime.model.ScheduledDirectFlight;
import no.rutebanken.netex.model.*;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.xml.bind.JAXBElement;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {AppTest.class})
@Ignore
public class ScheduledFlightToNetexConverterTest {

    @Autowired
    private AvinorAuthorityConfig avinorConfig;
    @Autowired
    private NhrAuthorityConfig nhrConfig;
    @Autowired
    private SasOperatorConfig sasConfig;
    @Autowired
    private WideroeOperatorConfig wideroeConfig;
    @Autowired
    private NorwegianOperatorConfig norwegianConfig;

    private ScheduledFlightToNetexConverter clazzUnderTest;

    @Before
    public void setUp() throws Exception {
        clazzUnderTest = new ScheduledFlightToNetexConverter();
        clazzUnderTest.setAvinorConfig(avinorConfig);
        clazzUnderTest.setNhrConfig(nhrConfig);
        clazzUnderTest.setSasConfig(sasConfig);
        clazzUnderTest.setWideroeConfig(wideroeConfig);
        clazzUnderTest.setNorwegianConfig(norwegianConfig);
    }

    @Test
    @Ignore
    public void testSimpNetexConversion() {
/*
        ScheduledDirectFlight directFlight = createScheduledDirectFlight("DY", "DY4455");
        JAXBElement<PublicationDeliveryStructure> publicationDeliveryStructure = clazzUnderTest.convertToNetex(directFlight);
        Assertions.assertThat(publicationDeliveryStructure).isNotNull();
        String xml = new XmlUtil().convertToXml(
                publicationDeliveryStructure, publicationDeliveryStructure.getValue().getClass());
        System.out.println(xml);
*/
    }

    @Test
    public void testCreateServiceJourneyPattern() {
        String flightId = "WF305";
        String routePath = "Trondheim - Sandefjord";
        Route route = createDummyRoute(flightId, routePath);
        List<ScheduledStopPoint> scheduledStopPoints = createDummyScheduledStopPoints(flightId, "Trondheim", "Sandefjord");

        ServiceJourneyPattern serviceJourneyPattern = clazzUnderTest.createServiceJourneyPattern(flightId, routePath, route, scheduledStopPoints);

        Assertions.assertThat(serviceJourneyPattern)
                .isNotNull();
    }

    @Test
    public void testCreateDirection() {
        Direction direction = clazzUnderTest.createDirection("WF739");

        Assertions.assertThat(direction)
                .isNotNull();
        Assertions.assertThat(direction.getId())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AVI:Route:WF739101:Direction");
    }

    @Test
    public void testCreateMultilingualString() {
        MultilingualString multilingualString = clazzUnderTest.createMultilingualString("TEST-STRING");

        Assertions.assertThat(multilingualString)
                .isNotNull();
        Assertions.assertThat(multilingualString.getValue())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("TEST-STRING");
    }

    @Test
    public void testCreateOrganisationRest() {
        String companyNumber = "999999999";
        String name = "TEST-COMPANY";
        String legalName = "TEST-LEGAL-NAME";
        String phone = "0047 999 99 999";
        String url = "http://test.no";
        OrganisationTypeEnumeration organisationType = OrganisationTypeEnumeration.OPERATOR;

        List<JAXBElement<?>> organisationRest = clazzUnderTest.createOrganisationRest(
                companyNumber, name, legalName, phone, url, organisationType
        );

        Assertions.assertThat(organisationRest)
                .isNotNull()
                .isNotEmpty()
                .hasSize(5);
        Assertions.assertThat(organisationRest.get(0).getValue())
                .isNotNull()
                .isInstanceOf(String.class)
                .isEqualTo(companyNumber);

        Assertions.assertThat(organisationRest.get(1).getValue())
                .isNotNull()
                .isInstanceOf(MultilingualString.class);
        Assertions.assertThat(((MultilingualString)organisationRest.get(1).getValue()).getValue())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo(name);

        Assertions.assertThat(organisationRest.get(2).getValue())
                .isNotNull()
                .isInstanceOf(MultilingualString.class);
        Assertions.assertThat(((MultilingualString)organisationRest.get(2).getValue()).getValue())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo(legalName);

        Assertions.assertThat(organisationRest.get(3).getValue())
                .isNotNull()
                .isInstanceOf(ContactStructure.class);

        Assertions.assertThat(organisationRest.get(4).getValue())
                .isNotNull()
                .isInstanceOf(List.class);
        Assertions.assertThat(((List<OrganisationTypeEnumeration>)organisationRest.get(4).getValue()).get(0))
                .isNotNull()
                .isEqualTo(organisationType);
    }

    @Test
    public void testCreateContactStructure() {
        String phone = "0047 999 99 999";
        String url = "http://test.no";

        JAXBElement<ContactStructure> contactStructureElement = clazzUnderTest.createContactStructure(phone, url);

        Assertions.assertThat(contactStructureElement.getValue())
                .isNotNull()
                .isInstanceOf(ContactStructure.class);
        Assertions.assertThat(contactStructureElement.getValue().getPhone())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo(phone);
        Assertions.assertThat(contactStructureElement.getValue().getUrl())
                .isNotNull()
                .isNotEmpty()
                .isEqualTo(url);
    }

    public ScheduledDirectFlight createScheduledDirectFlight(String airlineIATA, String flightId) {
        ScheduledDirectFlight directFlight = new ScheduledDirectFlight();
        directFlight.setFlightId(BigInteger.ONE);
        directFlight.setAirlineIATA(airlineIATA);
        directFlight.setAirlineFlightId(flightId);
        directFlight.setDateOfOperation(LocalDate.now());
        directFlight.setDepartureAirportIATA("BGO");
        directFlight.setTimeOfDeparture(LocalTime.NOON);
        directFlight.setArrivalAirportIATA("OSL");
        directFlight.setTimeOfArrival(LocalTime.MIDNIGHT);
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