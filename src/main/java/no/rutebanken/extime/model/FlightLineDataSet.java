package no.rutebanken.extime.model;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlightLineDataSet {

    // TODO find out which of these properties are needed/mandatory in this dataset

//    private BigInteger flightId;
//    private String airlineIATA;
//    private String airlineName;
//    private String airlineFlightId;
//    private String departureAirportIATA;
//    private String arrivalAirportIATA;
//    private String departureAirportName;
//    private String arrivalAirportName;
//    private OffsetTime timeOfDeparture;
//    private OffsetTime timeOfArrival;
//    private LocalDate dateOfOperation;
//    private List<ScheduledStopover> scheduledStopovers;
//    private Set<DayOfWeek> weekDaysPattern;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlightLineDataSet that = (FlightLineDataSet) o;
        return Objects.equal(availabilityPeriod, that.availabilityPeriod) &&
                Objects.equal(routePatterns, that.routePatterns) &&
                Objects.equal(journeyPatterns, that.journeyPatterns) &&
                Objects.equal(routeJourneys, that.routeJourneys);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(availabilityPeriod, routePatterns, journeyPatterns, routeJourneys);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("availabilityPeriod", availabilityPeriod)
                .add("routePatterns", routePatterns)
                .add("journeyPatterns", journeyPatterns)
                .add("routeJourneys", routeJourneys)
                .toString();
    }
}
