package no.rutebanken.extime.routes.avinor;

import no.avinor.flydata.xjc.model.airport.AirportNames;
import no.avinor.flydata.xjc.model.feed.Flight;
import no.rutebanken.extime.converter.FlightRouteToNeTExConverter;
import no.rutebanken.extime.model.*;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.processor.aggregate.AggregationStrategy;

import java.util.*;

import static org.apache.camel.component.stax.StAXBuilder.stax;


public class AvinorRealTimeRouteBuilder extends RouteBuilder {
//extends BaseRouteBuilder {

    static final String HEADER_AIRPORT_IATA = "AirportIATA";
    static final String HEADER_AIRLINE_IATA_MAP = "AirlineIATAMap";
    static final String HEADER_FLIGHTS_DIRECTION = "FlightsDirection";
    static final String HEADER_FLIGHTS_TIMEFROM = "FlightsTimeFrom";
    static final String HEADER_FLIGHTS_TIMETO = "FlightsTimeTo";

    static final String PROPERTY_ORIGINAL_BODY = "OriginalBody";

    @Override
    public void configure() throws Exception {
        //super.configure();
        //getContext().setTracing(true);

        from("{{avinor.timetable.scheduler.cron}}")
                .routeId("AvinorTimetableSchedulerStarter")
                .process(new AirportIATAProcessor()).id("AirportIATAProcessor")
                .split(body(), new AirportFlightAggregationStrategy()).parallelProcessing()
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Processing airport: ${body}")
                .setHeader(HEADER_AIRPORT_IATA, simple("${body}"))
                .to("direct:fetchTimetableForAirport").id("FetchTimetableProcessor")
                .end()
                .bean(new FlightRouteMatcher(), "findMatchingFlightRoutes")
                .process(new AirlineIATAProcessor()).id("AirlineIATAProcessor")
                .bean(new FlightRouteToNeTExConverter(), "convertRoutesToNetexFormat")
                .split(body())
                //.to("direct:validateNetexFormat") // split list and validate netex structure
                .convertBodyTo(String.class)
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Generated NeTEx XML: ${body}")
                .to("mock:jmsQueue")
                .end()
        ;

        from("direct:fetchTimetableForAirport")
                .routeId("FetchTimetableMulticaster")
                .multicast().aggregationStrategy(new FlightDirectionAggregationStrategy())
                .to("direct:fetchAirportDepartures").id("FetchDeparturesProcessor")
                .to("direct:fetchAirportArrivals").id("FetchArrivalsProcessor")
                .end()
                .process(new AirportEnricherInitProcessor())
                .enrich("direct:fetchAirportNameResource", new AirportEnricherAggregationStrategy())
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
/*
                .doTry()
                    .to("{{avinor.timetable.feed.endpoint}}").id("FetchTimetableFeedProcessor")
                .doCatch(Exception.class)
                    .log(LoggingLevel.ERROR, this.getClass().getName(), "Could not connect to {{avinor.timetable.feed.endpoint}}: ${exception.stacktrace}")
                    .log(LoggingLevel.ERROR, this.getClass().getName(), "Could not connect to {{avinor.timetable.feed.endpoint}}: ${exception.message}")
                    .to("mock:error")
                .end()
*/
                .to("{{avinor.timetable.feed.endpoint}}").id("FetchTimetableFeedProcessor")
                .split(stax(Flight.class, false), new FlightAggregationStrategy()).streaming()
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Fetched flight with id: ${body.flightId}")
                .end()
        ;

        from("direct:fetchAirportNameResource")
                .routeId("AirportNameEnricher")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .setHeader(Exchange.HTTP_QUERY, simple("airport=${body}&shortname=Y&ukname=Y"))
                .setBody(constant(null))
/*
                .doTry()
                    .to("{{avinor.airport.feed.endpoint}}").id("FetchAirportFeedProcessor")
                .doCatch(Exception.class)
                    .log(LoggingLevel.ERROR, this.getClass().getName(), "Could not connect to {{avinor.airport.feed.endpoint}}: ${exception}")
                    .to("mock:error")
                .end()
*/
                .to("{{avinor.airport.feed.endpoint}}").id("FetchAirportFeedProcessor")
                .unmarshal().jaxb("no.avinor.flydata.xjc.model.airport")
        ;

        from("direct:validateNetexFormat")
                .routeId("NetexValidator")
                .doTry()
                //.to("validator:https://raw.githubusercontent.com/rutebanken/NeTEx-XML/master/schema/1.03/xsd/NeTEx_publication_timetable.xsd?failOnNullBody=true")
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Validation against XSD passed")
                .doCatch(ValidationException.class)
                .log(LoggingLevel.DEBUG, this.getClass().getName(), "Validation failed")
                .to("mock:invalid")
                .end()
        ;
    }

    class AirportIATAProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setBody(AirportIATA.values());
        }
    }

    class AirlineIATAProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Map<String, String> airlineIATAMap = new HashMap<>();
            for (AirlineIATA airlineIATA : AirlineIATA.values()) {
                airlineIATAMap.put(airlineIATA.name(), airlineIATA.getAirportName());
            }
            exchange.getIn().setHeader(HEADER_AIRLINE_IATA_MAP, airlineIATAMap);
        }
    }

    class DepartureFlightDirectionProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setHeader(HEADER_FLIGHTS_DIRECTION, StopVisitType.DEPARTURE.getCode());
        }
    }

    class ArrivalFlightDirectionProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setHeader(HEADER_FLIGHTS_DIRECTION, StopVisitType.ARRIVAL.getCode());
        }
    }

    class AirportEnricherInitProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            AirportFlightDataSet originalBody = exchange.getIn().getBody(AirportFlightDataSet.class);
            exchange.setProperty(PROPERTY_ORIGINAL_BODY, originalBody);
            String enrichParameter = exchange.getIn().getHeader(HEADER_AIRPORT_IATA, String.class);
            exchange.getIn().setBody(enrichParameter);
        }
    }

    class AirlineEnricherInitProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
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

    class FlightAggregationStrategy implements AggregationStrategy {
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

    class AirportEnricherAggregationStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange original, Exchange resource) {
            AirportFlightDataSet originalBody = original.getProperty(PROPERTY_ORIGINAL_BODY, AirportFlightDataSet.class);
            AirportNames enrichment = resource.getIn().getBody(AirportNames.class);
            originalBody.setAirportName(enrichment.getAirportName().get(0));
            original.getIn().setBody(originalBody);
            return original;
        }
    }}
