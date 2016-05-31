package no.rutebanken.extime.routes.avinor;

import com.google.common.base.Strings;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.model.AirportIATA;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.camel.component.stax.StAXBuilder.stax;

@Component
public class AvinorTimetableRouteBuilder extends RouteBuilder { //extends BaseRouteBuilder {

    static final String HEADER_TIMETABLE_AIRPORT_IATA = "TimetableAirportIATA";
    static final String HEADER_TIMETABLE_PERIOD_FROM = "TimetablePeriodFrom";

    static final String HEADER_AIRLINE_IATA_MAP = "AirlineIATAMap";
    static final String HEADER_FLIGHTS_DIRECTION = "FlightsDirection";
    static final String HEADER_FLIGHTS_TIMEFROM = "FlightsTimeFrom";
    static final String HEADER_FLIGHTS_TIMETO = "FlightsTimeTo";

    @Override
    public void configure() throws Exception {
        //super.configure();
        getContext().setTracing(true);

        from("{{avinor.timetable.scheduler.cron}}")
                .routeId("AvinorTimetableSchedulerStarter")
                .process(exchange -> {
                    exchange.getIn().setBody(AirportIATA.values());
                }).id("TimetableAirportIATAProcessor")
                .setHeader(HEADER_TIMETABLE_PERIOD_FROM, simple("${date:now:yyyy-MM-dd}Z"))
                // @todo: enable parallell processing after testing
                .split(body(), new ScheduledFlightListAggregationStrategy())//.parallelProcessing()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(),
                            "Processing scheduled flights for airport with IATA code: ${body}")
                    .setHeader(HEADER_TIMETABLE_AIRPORT_IATA, simple("${body}"))
                    .to("direct:fetchTimetableForAirport").id("FetchTimetableProcessor")
                .end()
                .bean(ScheduledFlightConverter.class, "convertToScheduledFlights")
                //.bean(ScheduledRouteToNetexConverter.class)
                .to("mock:jms:queue")
        ;

        from("direct:fetchTimetableForAirport")
                .routeId("FetchTimetableForAirport")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetching flights for airport IATA: ${body}")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .setHeader(Exchange.HTTP_QUERY, simpleF("airport=${header.%s}&direction=D&PeriodFrom=${header.%s}&PeriodTo={{avinor.timetable.periodto}}",
                        HEADER_TIMETABLE_AIRPORT_IATA, HEADER_TIMETABLE_PERIOD_FROM))
                .setBody(constant(null))
                .to("{{avinor.timetable.feed.endpoint}}").id("FetchTimetableFeedProcessor")
                .split(stax(Flight.class, false), new ScheduledAirportFlightsAggregationStrategy()).streaming()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(),
                            "Fetched flight with id: ${body.airlineDesignator}${body.flightNumber}")
                .end()
        ;
    }

    @SuppressWarnings("Duplicates")
    class ScheduledFlightListAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            @SuppressWarnings("unchecked")
            List<Flight> newExchangeBody = newExchange.getIn().getBody(List.class);
            if (oldExchange == null) {
                List<Flight> scheduledFlights = new ArrayList<>();
                scheduledFlights.addAll(newExchangeBody);
                newExchange.getIn().setBody(scheduledFlights);
                return newExchange;
            } else {
                @SuppressWarnings("unchecked")
                List<Flight> scheduledFlights = Collections.checkedList(
                        oldExchange.getIn().getBody(List.class), Flight.class);
                scheduledFlights.addAll(scheduledFlights);
                return oldExchange;
            }
        }
    }

    @SuppressWarnings("Duplicates")
    class ScheduledAirportFlightsAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            Flight newFlight = newExchange.getIn().getBody(Flight.class);
            if (oldExchange == null) {
                List<Flight> flightList = new ArrayList<>();
                if (isDomesticFlight(newFlight)) {
                    flightList.add(newFlight);
                }
                newExchange.getIn().setBody(flightList);
                return newExchange;
            } else {
                @SuppressWarnings("unchecked")
                List<Flight> flightList = Collections.checkedList(
                        oldExchange.getIn().getBody(List.class), Flight.class);
                if (isDomesticFlight(newFlight)) {
                    flightList.add(newFlight);
                }
                return oldExchange;
            }
        }

        private boolean isValidFlight(Flight flight) {
            return flight.getId() != null &&
                    !Strings.isNullOrEmpty(flight.getAirlineDesignator()) &&
                    !Strings.isNullOrEmpty(flight.getFlightNumber()) &&
                    flight.getDateOfOperation() != null &&
                    !Strings.isNullOrEmpty(flight.getDepartureStation()) &&
                    flight.getStd() != null &&
                    !Strings.isNullOrEmpty(flight.getArrivalStation()) &&
                    flight.getSta() != null &&
                    isDomesticFlight(flight);
        }

        private boolean isDomesticFlight(Flight flight) {
            return isValidDepartureAndArrival(flight.getDepartureStation(), flight.getArrivalStation());
        }

        private boolean isValidDepartureAndArrival(String departureIATA, String arrivalIATA) {
            return EnumUtils.isValidEnum(AirportIATA.class, departureIATA)
                    && EnumUtils.isValidEnum(AirportIATA.class, arrivalIATA);
        }
    }

}
