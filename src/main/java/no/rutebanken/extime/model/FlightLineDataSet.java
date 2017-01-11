package no.rutebanken.extime.model;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlightLineDataSet {

    private String airlineIata;
    private String airlineName;
    private String lineDesignation;
    private String lineName;
    private AvailabilityPeriod availabilityPeriod;
    private Set<String> routePatterns;
    private Set<String> journeyPatterns;
    private Map<String, Map<String, List<ScheduledFlight>>> routeJourneys;

    public AvailabilityPeriod getAvailabilityPeriod() {
        return availabilityPeriod;
    }

    public void setAvailabilityPeriod(AvailabilityPeriod availabilityPeriod) {
        this.availabilityPeriod = availabilityPeriod;
    }

    public Set<String> getRoutePatterns() {
        return routePatterns;
    }

    public void setRoutePatterns(Set<String> routePatterns) {
        this.routePatterns = routePatterns;
    }

    public Set<String> getJourneyPatterns() {
        return journeyPatterns;
    }

    public void setJourneyPatterns(Set<String> journeyPatterns) {
        this.journeyPatterns = journeyPatterns;
    }

    public Map<String, Map<String, List<ScheduledFlight>>> getRouteJourneys() {
        return routeJourneys;
    }

    public void setRouteJourneys(Map<String, Map<String, List<ScheduledFlight>>> routeJourneys) {
        this.routeJourneys = routeJourneys;
    }

    public String getAirlineIata() {
        return airlineIata;
    }

    public void setAirlineIata(String airlineIata) {
        this.airlineIata = airlineIata;
    }

    public String getAirlineName() {
        return airlineName;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlightLineDataSet that = (FlightLineDataSet) o;
        return Objects.equal(airlineIata, that.airlineIata) &&
                Objects.equal(airlineName, that.airlineName) &&
                Objects.equal(lineDesignation, that.lineDesignation) &&
                Objects.equal(lineName, that.lineName) &&
                Objects.equal(availabilityPeriod, that.availabilityPeriod) &&
                Objects.equal(routePatterns, that.routePatterns) &&
                Objects.equal(journeyPatterns, that.journeyPatterns) &&
                Objects.equal(routeJourneys, that.routeJourneys);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(airlineIata, airlineName, lineDesignation, lineName, availabilityPeriod, routePatterns, journeyPatterns, routeJourneys);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("airlineIata", airlineIata)
                .add("airlineName", airlineName)
                .add("lineDesignation", lineDesignation)
                .add("lineName", lineName)
                .add("availabilityPeriod", availabilityPeriod)
                .add("routePatterns", routePatterns)
                .add("journeyPatterns", journeyPatterns)
                .add("routeJourneys", routeJourneys)
                .toString();
    }
}
