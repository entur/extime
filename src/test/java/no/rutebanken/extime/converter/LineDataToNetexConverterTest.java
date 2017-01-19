package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.config.CamelRouteDisabler;
import no.rutebanken.extime.model.AvailabilityPeriod;
import no.rutebanken.extime.model.FlightRoute;
import no.rutebanken.extime.model.LineDataSet;
import no.rutebanken.extime.model.ScheduledFlight;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rutebanken.netex.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.xml.bind.JAXBElement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.extime.Constants.OFFSET_MIDNIGHT_UTC;
import static org.assertj.core.groups.Tuple.tuple;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {CamelRouteDisabler.class, LineDataToNetexConverter.class})
public class LineDataToNetexConverterTest {

    @Autowired
    private LineDataToNetexConverter netexConverter;

    @Test
    public void testLineWithDirectRoutes() throws Exception {
        LineDataSet lineDataSet = new LineDataSet();
        lineDataSet.setAirlineIata("DY");
        lineDataSet.setAirlineName("Norwegian");
        lineDataSet.setLineDesignation("OSL-BGO");
        lineDataSet.setLineName("Oslo-Bergen");

        LocalDate requestPeriodFromDate = LocalDate.parse("2017-01-30");
        LocalDate requestPeriodToDate = requestPeriodFromDate.plusDays(1);

        OffsetTime offsetMidnight = OffsetTime.parse(OFFSET_MIDNIGHT_UTC).withOffsetSameLocal(ZoneOffset.UTC);
        OffsetDateTime requestPeriodFromDateTime = requestPeriodFromDate.atTime(offsetMidnight);
        OffsetDateTime requestPeriodToDateTime = requestPeriodToDate.atTime(offsetMidnight);

        AvailabilityPeriod availabilityPeriod = new AvailabilityPeriod(requestPeriodFromDateTime, requestPeriodToDateTime);
        lineDataSet.setAvailabilityPeriod(availabilityPeriod);

        FlightRoute mainRoute = new FlightRoute("OSL-BGO", "Oslo-Bergen");
        FlightRoute oppositeRoute = new FlightRoute("BGO-OSL", "Bergen-Oslo");
        List<FlightRoute> flightRoutes = Arrays.asList(mainRoute, oppositeRoute);
        lineDataSet.setFlightRoutes(flightRoutes);

        Map<String, Map<String, List<ScheduledFlight>>> routeJourneys = new HashMap<>();

        List<ScheduledFlight> routeOslBgoFlights = Lists.newArrayList(
                createScheduledDirectFlight("DY", "DY602", LocalDate.parse("2017-01-30"), "OSL", "Oslo",
                        "BGO", "Bergen", OffsetTime.parse("07:10:00Z"), OffsetTime.parse("08:05:00Z")));

        Map<String, List<ScheduledFlight>> dy602Flights = new HashMap<>();
        dy602Flights.put("DY602", routeOslBgoFlights);
        routeJourneys.put("OSL-BGO", dy602Flights);

        List<ScheduledFlight> routeBgoOslFlights = Lists.newArrayList(
                createScheduledDirectFlight("DY", "DY633", LocalDate.parse("2017-01-31"), "BGO", "Bergen",
                        "OSL", "Oslo", OffsetTime.parse("17:00:00Z"), OffsetTime.parse("17:55:00Z")));

        Map<String, List<ScheduledFlight>> dy633Flights = new HashMap<>();
        dy633Flights.put("DY633", routeBgoOslFlights);
        routeJourneys.put("BGO-OSL", dy633Flights);

        lineDataSet.setRouteJourneys(routeJourneys);

        JAXBElement<PublicationDeliveryStructure> publicationDeliveryElement = netexConverter.convertToNetex(lineDataSet);
        PublicationDeliveryStructure publicationDelivery = publicationDeliveryElement.getValue();

        // check the resulting publication delivery
        Assertions.assertThat(publicationDelivery)
                .isNotNull();

        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames =
                publicationDelivery.getDataObjects().getCompositeFrameOrCommonFrame();
        CompositeFrame compositeFrame = getFrames(CompositeFrame.class, dataObjectFrames).get(0);

        List<Object> validityConditions = compositeFrame.getValidityConditions().getValidityConditionRefOrValidBetweenOrValidityCondition_();
        AvailabilityCondition availabilityCondition = (AvailabilityCondition) ((JAXBElement) validityConditions.get(0)).getValue();

        // check availability conditions
        Assertions.assertThat(availabilityCondition.getFromDate())
                .isEqualTo(requestPeriodFromDateTime);
        Assertions.assertThat(availabilityCondition.getToDate())
                .isEqualTo(requestPeriodToDateTime);

        List<JAXBElement<? extends Common_VersionFrameStructure>> frames = compositeFrame.getFrames().getCommonFrame();
        ServiceFrame serviceFrame = getFrames(ServiceFrame.class, frames).get(0);

        // check network
        Network network = serviceFrame.getNetwork();
        Assertions.assertThat(network.getId()).isEqualTo("AVI:Network:DY");
        Assertions.assertThat(network.getName().getValue()).isEqualTo("Norwegian");

        // check line
        Line line = (Line) serviceFrame.getLines().getLine_().get(0).getValue();
        Assertions.assertThat(line.getId()).isEqualTo("AVI:Line:DY-OSL-BGO");
        Assertions.assertThat(line.getName().getValue()).isEqualTo("Oslo-Bergen");
        Assertions.assertThat(line.getPublicCode()).isEqualTo("OSL-BGO");
        Assertions.assertThat(line.getOperatorRef().getRef()).isEqualTo("AVI:Operator:DY");

        Assertions.assertThat(line.getRoutes().getRouteRef())
                .isNotNull()
                .isNotEmpty()
                .hasSize(2)
                .extracting("ref")
                .contains("AVI:Route:OSL-BGO", "AVI:Route:BGO-OSL");

        // check routes
        List<JAXBElement<? extends LinkSequence_VersionStructure>> routeElements = serviceFrame.getRoutes().getRoute_();

        List<Route> routes = routeElements.stream()
                .map(JAXBElement::getValue)
                .map(route -> (Route) route)
                .collect(Collectors.toList());

        Assertions.assertThat(routes)
                .isNotNull()
                .isNotEmpty()
                .hasSize(2)
                .extracting("id", "name.value", "lineRef.value.ref")
                .contains(tuple("AVI:Route:OSL-BGO", "Oslo-Bergen", "AVI:Line:DY-OSL-BGO"), tuple("AVI:Route:BGO-OSL", "Bergen-Oslo", "AVI:Line:DY-OSL-BGO"));
    }

    private ScheduledFlight createScheduledDirectFlight(String airlineIATA, String airlineFlightId,
            LocalDate dateOfOperation, String departureAirportIata, String departureAirportName,
            String arrivalAirportIata, String arrivalAirportName, OffsetTime timeOfDeparture, OffsetTime timeOfArrival) {

        ScheduledFlight scheduledFlight = new ScheduledFlight();
        scheduledFlight.setAirlineIATA(airlineIATA);
        scheduledFlight.setAirlineFlightId(airlineFlightId);
        scheduledFlight.setDateOfOperation(dateOfOperation);
        scheduledFlight.setDepartureAirportIATA(departureAirportIata);
        scheduledFlight.setDepartureAirportName(departureAirportName);
        scheduledFlight.setArrivalAirportIATA(arrivalAirportIata);
        scheduledFlight.setArrivalAirportName(arrivalAirportName);
        scheduledFlight.setTimeOfDeparture(timeOfDeparture);
        scheduledFlight.setTimeOfArrival(timeOfArrival);
        return scheduledFlight;
    }

    public <T> List<T> getFrames(Class<T> clazz, List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames) {
        List<T> foundFrames = new ArrayList<>();

        for (JAXBElement<? extends Common_VersionFrameStructure> frame : dataObjectFrames) {
            if (frame.getValue().getClass().equals(clazz)) {
                foundFrames.add(clazz.cast(frame.getValue()));
            }
        }

        return foundFrames;
    }

}