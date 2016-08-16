package no.rutebanken.extime.converter;

import no.rutebanken.extime.AppTest;
import no.rutebanken.extime.config.*;
import no.rutebanken.extime.model.ScheduledDirectFlight;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

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
    @Ignore
    public void convertDirectFlightToNetex() {}

    @Test
    @Ignore
    public void convertStopoverFlightToNetex() {}

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