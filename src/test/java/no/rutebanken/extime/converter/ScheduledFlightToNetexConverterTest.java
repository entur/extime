package no.rutebanken.extime.converter;

import no.rutebanken.extime.AppTest;
import no.rutebanken.extime.config.*;
import no.rutebanken.extime.model.ScheduledDirectFlight;
import no.rutebanken.netex.model.ContactStructure;
import no.rutebanken.netex.model.Direction;
import no.rutebanken.netex.model.MultilingualString;
import no.rutebanken.netex.model.OrganisationTypeEnumeration;
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
}