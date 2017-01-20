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

        LocalDate flight1DateOfOperation = LocalDate.parse("2017-01-30");
        OffsetTime flight1DepartureTime = OffsetTime.parse("07:10:00Z");
        OffsetTime flight1ArrivalTime = OffsetTime.parse("08:05:00Z");

        List<ScheduledFlight> routeOslBgoFlights = Lists.newArrayList(
                createScheduledDirectFlight("DY", "DY602", flight1DateOfOperation, "OSL", "Oslo",
                        "BGO", "Bergen", flight1DepartureTime, flight1ArrivalTime));

        Map<String, List<ScheduledFlight>> dy602Flights = new HashMap<>();
        dy602Flights.put("DY602", routeOslBgoFlights);
        routeJourneys.put("OSL-BGO", dy602Flights);

        LocalDate flight2DateOfOperation = LocalDate.parse("2017-01-31");
        OffsetTime flight2DepartureTime = OffsetTime.parse("17:00:00Z");
        OffsetTime flight2ArrivalTime = OffsetTime.parse("17:55:00Z");

        List<ScheduledFlight> routeBgoOslFlights = Lists.newArrayList(
                createScheduledDirectFlight("DY", "DY633", flight2DateOfOperation, "BGO", "Bergen",
                        "OSL", "Oslo", flight2DepartureTime, flight2ArrivalTime));

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

        // check elements in service frame
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
                .hasSize(2)
                .extracting("id", "name.value", "lineRef.value.ref")
                .contains(tuple("AVI:Route:OSL-BGO", "Oslo-Bergen", "AVI:Line:DY-OSL-BGO"), tuple("AVI:Route:BGO-OSL", "Bergen-Oslo", "AVI:Line:DY-OSL-BGO"));

        // check journey patterns
        List<JAXBElement<?>> journeyPatternElements = serviceFrame.getJourneyPatterns().getJourneyPattern_OrJourneyPatternView();

        List<JourneyPattern> journeyPatterns = journeyPatternElements.stream()
                .map(JAXBElement::getValue)
                .map(journeyPattern -> (JourneyPattern) journeyPattern)
                .collect(Collectors.toList());

        Assertions.assertThat(journeyPatterns)
                .hasSize(2)
                .extracting("routeRef.ref")
                .contains("AVI:Route:OSL-BGO", "AVI:Route:BGO-OSL");

        journeyPatterns.forEach(journeyPattern -> Assertions.assertThat(journeyPattern.getPointsInSequence()
                .getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()).hasSize(2));

        // check elements in timetable frame
        TimetableFrame timetableFrame = getFrames(TimetableFrame.class, frames).get(0);

        List<ServiceJourney> serviceJourneys = timetableFrame.getVehicleJourneys().getDatedServiceJourneyOrDeadRunOrServiceJourney().stream()
                .map(journey -> (ServiceJourney) journey)
                .collect(Collectors.toList());

        // check first service journey
        Assertions.assertThat(serviceJourneys.get(0).getDepartureTime()).isEqualTo(flight1DepartureTime);
        //Assertions.assertThat(serviceJourneys.get(0).getJourneyPatternRef().getValue().getRef()).isEqualTo("AVI:JourneyPattern:OSL-BGO"); // TODO fix journey pattern id
        Assertions.assertThat(serviceJourneys.get(0).getPublicCode()).isEqualTo("DY602");
        Assertions.assertThat(serviceJourneys.get(0).getLineRef().getValue().getRef()).isEqualTo("AVI:Line:DY-OSL-BGO");
        Assertions.assertThat(serviceJourneys.get(0).getDayTypes().getDayTypeRef().get(0).getValue().getRef()).isEqualTo("AVI:DayType:Mon_30");

        List<TimetabledPassingTime> departurePassingTimes = serviceJourneys.get(0).getPassingTimes().getTimetabledPassingTime();
        Assertions.assertThat(departurePassingTimes).hasSize(2);
        //Assertions.assertThat(departurePassingTimes.get(0).getPointInJourneyPatternRef().getValue().getRef()).isEqualTo(""); // TODO fix journey pattern id
        Assertions.assertThat(departurePassingTimes.get(0).getDepartureTime()).isEqualTo(flight1DepartureTime);
        //Assertions.assertThat(departurePassingTimes.get(1).getPointInJourneyPatternRef().getValue().getRef()).isEqualTo(""); // TODO fix journey pattern id
        Assertions.assertThat(departurePassingTimes.get(1).getArrivalTime()).isEqualTo(flight1ArrivalTime);

        // check second service journey
        Assertions.assertThat(serviceJourneys.get(1).getDepartureTime()).isEqualTo(flight2DepartureTime);
        //Assertions.assertThat(serviceJourneys.get(1).getJourneyPatternRef().getValue().getRef()).isEqualTo("AVI:JourneyPattern:OSL-BGO"); // TODO fix journey pattern id
        Assertions.assertThat(serviceJourneys.get(1).getPublicCode()).isEqualTo("DY633");
        Assertions.assertThat(serviceJourneys.get(1).getLineRef().getValue().getRef()).isEqualTo("AVI:Line:DY-OSL-BGO");
        Assertions.assertThat(serviceJourneys.get(1).getDayTypes().getDayTypeRef().get(0).getValue().getRef()).isEqualTo("AVI:DayType:Tue_31");

        List<TimetabledPassingTime> arrivalPassingTimes = serviceJourneys.get(1).getPassingTimes().getTimetabledPassingTime();
        Assertions.assertThat(arrivalPassingTimes).hasSize(2);
        //Assertions.assertThat(departurePassingTimes.get(0).getPointInJourneyPatternRef().getValue().getRef()).isEqualTo(""); // TODO fix journey pattern id
        Assertions.assertThat(arrivalPassingTimes.get(0).getDepartureTime()).isEqualTo(flight2DepartureTime);
        //Assertions.assertThat(departurePassingTimes.get(1).getPointInJourneyPatternRef().getValue().getRef()).isEqualTo(""); // TODO fix journey pattern id
        Assertions.assertThat(arrivalPassingTimes.get(1).getArrivalTime()).isEqualTo(flight2ArrivalTime);

        // check elements in service calendar frame
        ServiceCalendarFrame serviceCalendarFrame = getFrames(ServiceCalendarFrame.class, frames).get(0);

        // check day types
        List<DayType> dayTypes = serviceCalendarFrame.getDayTypes().getDayType_().stream()
                .map(JAXBElement::getValue)
                .map(dayType -> (DayType) dayType)
                .collect(Collectors.toList());

        Assertions.assertThat(dayTypes)
                .hasSize(2)
                .extracting("id")
                .contains("AVI:DayType:Mon_30", "AVI:DayType:Tue_31");

        LocalDate date = serviceCalendarFrame.getDayTypeAssignments().getDayTypeAssignment().get(0).getDate();
        String ref = serviceCalendarFrame.getDayTypeAssignments().getDayTypeAssignment().get(0).getDayTypeRef().getValue().getRef();

        // check day type assignments
        Assertions.assertThat(serviceCalendarFrame.getDayTypeAssignments().getDayTypeAssignment())
                .hasSize(2)
                .extracting("date", "dayTypeRef.value.ref")
                .contains(tuple(flight1DateOfOperation, "AVI:DayType:Mon_30"), tuple(flight2DateOfOperation, "AVI:DayType:Tue_31"));
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