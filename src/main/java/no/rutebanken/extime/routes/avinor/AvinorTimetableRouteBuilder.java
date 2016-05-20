package no.rutebanken.extime.routes.avinor;

import no.avinor.flydata.xjc.model.feed.Flight;
import no.rutebanken.extime.model.AirportFlightDataSet;
import no.rutebanken.extime.model.FlightDirection;
import no.rutebanken.extime.model.FlightType;
import no.rutebanken.extime.model.IATA;
import no.rutebanken.extime.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.apache.camel.component.stax.StAXBuilder.stax;

@Component
public class AvinorTimetableRouteBuilder extends BaseRouteBuilder {

    static final String HEADER_AIRPORT_IATA = "AirportIATA";
    static final String HEADER_FLIGHTS_DIRECTION = "FlightsDirection";
    static final String HEADER_FLIGHTS_TIMEFROM = "FlightsTimeFrom";
    static final String HEADER_FLIGHTS_TIMETO = "FlightsTimeTo";

    @Override
    public void configure() throws Exception {
        super.configure();
        getContext().setTracing(true);

        from("{{avinor.timetable.scheduler.cron}}")
                .routeId("AvinorTimetableSchedulerStarter")
                .process(new AirportIATAProcessor()).id("AirportIATAProcessor")
                .split(body(), new AirportFlightAggregationStrategy()).parallelProcessing()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Processing airport: ${body}")
                    .setHeader(HEADER_AIRPORT_IATA, simple("${body}"))
                    .to("direct:fetchTimetableForAirport").id("FetchTimetableProcessor")
                .end()
                .to("mock:processAggregatedFlights")
        ;

        from("direct:fetchTimetableForAirport")
                .routeId("FetchTimetableMulticaster")
                .multicast().aggregationStrategy(new FlightDirectionAggregationStrategy())
                    .to("direct:fetchAirportDepartures").id("FetchDeparturesProcessor")
                    .to("direct:fetchAirportArrivals").id("FetchArrivalsProcessor")
                .end()
        ;

        from("direct:fetchAirportDepartures")
                .routeId("DepartureFlightsFetcher")
                .process(new DepartureFlightDirectionProcessor())
                .setHeader(HEADER_FLIGHTS_TIMEFROM, simple("${properties:avinor.timetable.departures.timefrom}"))
                .setHeader(HEADER_FLIGHTS_TIMETO, simple("${properties:avinor.timetable.departures.timeto}"))
                .to("direct:fetchAirportFlights")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetched departure flights")
        ;

        from("direct:fetchAirportArrivals")
                .routeId("ArrivalFlightsFetcher")
                .process(new ArrivalFlightDirectionProcessor())
                .setHeader(HEADER_FLIGHTS_TIMEFROM, simple("${properties:avinor.timetable.arrivals.timefrom}"))
                .setHeader(HEADER_FLIGHTS_TIMETO, simple("${properties:avinor.timetable.arrivals.timeto}"))
                .to("direct:fetchAirportFlights")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetched arrival flights")
        ;

        from("direct:fetchAirportFlights")
                .routeId("TimetableFetcher")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetching flights for ${body}")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .setHeader(Exchange.HTTP_QUERY, simpleF("airport=${header.%s}&timeFrom=${header.%s}&timeTo=${header.%s}&direction=${header.%s}",
                        HEADER_AIRPORT_IATA, HEADER_FLIGHTS_TIMEFROM, HEADER_FLIGHTS_TIMETO, HEADER_FLIGHTS_DIRECTION))
                .setBody(constant(null))
                .doTry()
                    .to("{{avinor.timetable.feed.endpoint}}").id("TimetableFetchProcessor")
                .doCatch(Exception.class)
                    .log(LoggingLevel.ERROR, this.getClass().getName(), "Could not connect to {{avinor.timetable.feed.endpoint}}: ${exception}")
                .end()
                .split(stax(Flight.class, false), new FlightAggregationStrategy()).streaming()
                    .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetched flight with id: ${body.flightId}")
                .end()
        ;
    }

    class AirportIATAProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setBody(IATA.values());
        }
    }

    class DepartureFlightDirectionProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setHeader(HEADER_FLIGHTS_DIRECTION, FlightDirection.DEPARTURE.getCode());
        }
    }

    class ArrivalFlightDirectionProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setHeader(HEADER_FLIGHTS_DIRECTION, FlightDirection.ARRIVAL.getCode());
        }
    }

    class AirportFlightAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            String airportIATACode = newExchange.getIn().getHeader(HEADER_AIRPORT_IATA, String.class);
            @SuppressWarnings("unchecked")
            AirportFlightDataSet newExchangeBody = newExchange.getIn().getBody(AirportFlightDataSet.class);
            if (oldExchange == null) {
                Map<String, AirportFlightDataSet> airportFlightsMap = new HashMap<>();
                airportFlightsMap.put(airportIATACode, newExchangeBody);
                newExchange.getIn().setBody(airportFlightsMap);
                return newExchange;
            } else {
                @SuppressWarnings("unchecked")
                Map<String, AirportFlightDataSet> airportFlightsMap = oldExchange.getIn().getBody(Map.class);
                airportFlightsMap.put(airportIATACode, newExchangeBody);
                return oldExchange;
            }
        }
    }

    class FlightDirectionAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            @SuppressWarnings("unchecked")
            List<Flight> newExchangeBody = Collections.checkedList(
                    newExchange.getIn().getBody(List.class), Flight.class);
            if (oldExchange == null) {
                AirportFlightDataSet dataSet = new AirportFlightDataSet();
                dataSet.getDepartureFlights().addAll(newExchangeBody);
                newExchange.getIn().setBody(dataSet, AirportFlightDataSet.class);
                return newExchange;
            } else {
                AirportFlightDataSet oldExchangeBody = oldExchange.getIn().getBody(AirportFlightDataSet.class);
                oldExchangeBody.getArrivalFlights().addAll(newExchangeBody);
                return oldExchange;
            }
        }
    }

    private class FlightAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            Flight body = newExchange.getIn().getBody(Flight.class);
            if (oldExchange == null) {
                List<Flight> airportFlights = new ArrayList<>();
                // @todo: filter away all flights with wrong dates, due to a bug in the Avinor feed
                // @todo check against requested start date, and if it is less, if so do not add flight
                if (isDomesticFlight(body)) {
                    airportFlights.add(body);
                }
                newExchange.getIn().setBody(airportFlights);
                return newExchange;
            } else {
                @SuppressWarnings("unchecked")
                List<Flight> airportFlights = Collections.checkedList(
                        oldExchange.getIn().getBody(List.class), Flight.class);
                // @todo: filter away all flights with wrong dates, due to a bug in the Avinor feed
                // @todo check against requested start date, and if it is less, if so do not add flight
                if (isDomesticFlight(body)) {
                    airportFlights.add(body);
                }
                return oldExchange;
            }
        }

        private boolean isDomesticFlight(Flight flight) {
            return flight.getDomInt().equalsIgnoreCase(FlightType.DOMESTIC.getCode());
        }
    }
}
