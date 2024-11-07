package no.rutebanken.extime.model;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.util.List;
import java.util.Map;

public class LineDataSet {

    private String airlineIata;
    private String airlineName;
    private String lineDesignation;
    private String lineName;
    private AvailabilityPeriod availabilityPeriod;
    private List<FlightRoute> flightRoutes;
    private Map<String, Map<String, List<ScheduledFlight>>> routeJourneys;

    public String getAirlineIata() {
        return airlineIata;
    }

    public void setAirlineIata(String airlineIata) {
        this.airlineIata = airlineIata;
    }

    public void setAirlineName(String airlineName) {
        this.airlineName = airlineName;
    }

    public String getLineDesignation() {
        return lineDesignation;
    }

    public void setLineDesignation(String lineDesignation) {
        this.lineDesignation = lineDesignation;
    }

    public String getLineName() {
        return lineName;
    }

    public void setLineName(String lineName) {
        this.lineName = lineName;
    }

    public AvailabilityPeriod getAvailabilityPeriod() {
        return availabilityPeriod;
    }

    public void setAvailabilityPeriod(AvailabilityPeriod availabilityPeriod) {
        this.availabilityPeriod = availabilityPeriod;
    }

    public List<FlightRoute> getFlightRoutes() {
        return flightRoutes;
    }

    public void setFlightRoutes(List<FlightRoute> flightRoutes) {
        this.flightRoutes = flightRoutes;
    }

    public Map<String, Map<String, List<ScheduledFlight>>> getRouteJourneys() {
        return routeJourneys;
    }

    public void setRouteJourneys(Map<String, Map<String, List<ScheduledFlight>>> routeJourneys) {
        this.routeJourneys = routeJourneys;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LineDataSet that = (LineDataSet) o;
        return Objects.equal(airlineIata, that.airlineIata) &&
                Objects.equal(airlineName, that.airlineName) &&
                Objects.equal(lineDesignation, that.lineDesignation) &&
                Objects.equal(lineName, that.lineName) &&
                Objects.equal(availabilityPeriod, that.availabilityPeriod) &&
                Objects.equal(flightRoutes, that.flightRoutes) &&
                Objects.equal(routeJourneys, that.routeJourneys);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(airlineIata, airlineName, lineDesignation, lineName, availabilityPeriod, flightRoutes, routeJourneys);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("airlineIata", airlineIata)
                .add("airlineName", airlineName)
                .add("lineDesignation", lineDesignation)
                .add("lineName", lineName)
                .add("availabilityPeriod", availabilityPeriod)
                .add("flightRoutes", flightRoutes)
                .add("routeJourneys", routeJourneys)
                .toString();
    }
}
