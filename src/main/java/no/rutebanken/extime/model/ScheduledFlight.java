package no.rutebanken.extime.model;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static no.rutebanken.extime.Constants.DASH;
import static no.rutebanken.extime.Constants.EMPTY;

public class ScheduledFlight {

    private Long flightId;
    private String airlineIATA;
    private String airlineName;
    private String airlineFlightId;
    private String departureAirportIATA;
    private String arrivalAirportIATA;
    private String departureAirportName;
    private String arrivalAirportName;
    private LocalDate dateOfOperation;
    private LocalTime timeOfDeparture;
    private LocalTime timeOfArrival;
    private String lineDesignation;
    private String stopsDesignation;
    private String timesDesignation;
    private List<ScheduledStopover> scheduledStopovers;

    public Long getFlightId() {
        return flightId;
    }

    public void setFlightId(Long flightId) {
        this.flightId = flightId;
    }

    public String getAirlineIATA() {
        return airlineIATA;
    }

    public void setAirlineIATA(String airlineIATA) {
        this.airlineIATA = airlineIATA;
    }

    public String getAirlineName() {
        return airlineName;
    }

    public void setAirlineName(String airlineName) {
        this.airlineName = airlineName;
    }

    public String getAirlineFlightId() {
        return airlineFlightId;
    }

    public void setAirlineFlightId(String airlineFlightId) {
        this.airlineFlightId = airlineFlightId;
    }

    public String getDepartureAirportIATA() {
        return departureAirportIATA;
    }

    public void setDepartureAirportIATA(String departureAirportIATA) {
        this.departureAirportIATA = departureAirportIATA;
    }

    public String getArrivalAirportIATA() {
        return arrivalAirportIATA;
    }

    public void setArrivalAirportIATA(String arrivalAirportIATA) {
        this.arrivalAirportIATA = arrivalAirportIATA;
    }

    public String getDepartureAirportName() {
        return departureAirportName;
    }

    public void setDepartureAirportName(String departureAirportName) {
        this.departureAirportName = departureAirportName;
    }

    public String getArrivalAirportName() {
        return arrivalAirportName;
    }

    public void setArrivalAirportName(String arrivalAirportName) {
        this.arrivalAirportName = arrivalAirportName;
    }

    public LocalDate getDateOfOperation() {
        return dateOfOperation;
    }

    public void setDateOfOperation(LocalDate dateOfOperation) {
        this.dateOfOperation = dateOfOperation;
    }

    public LocalTime getTimeOfDeparture() {
        return timeOfDeparture;
    }

    public void setTimeOfDeparture(LocalTime timeOfDeparture) {
        this.timeOfDeparture = timeOfDeparture;
    }

    public LocalTime getTimeOfArrival() {
        return timeOfArrival;
    }

    public void setTimeOfArrival(LocalTime timeOfArrival) {
        this.timeOfArrival = timeOfArrival;
    }

    public String getLineDesignation() {
        return lineDesignation;
    }

    public void setLineDesignation(String lineDesignation) {
        this.lineDesignation = lineDesignation;
    }

    public String getStopsDesignation() {
        return stopsDesignation;
    }

    public void setStopsDesignation(String stopsDesignation) {
        this.stopsDesignation = stopsDesignation;
    }

    public String getTimesDesignation() {
        return timesDesignation;
    }

    public void setTimesDesignation(String timesDesignation) {
        this.timesDesignation = timesDesignation;
    }

    public List<ScheduledStopover> getScheduledStopovers() {
        if (scheduledStopovers == null) {
            scheduledStopovers = new ArrayList<>();
        }
        return this.scheduledStopovers;
    }

    public void setScheduledStopovers(List<ScheduledStopover> scheduledStopovers) {
        this.scheduledStopovers = scheduledStopovers;
    }

    public boolean hasStopovers() {
        return this.scheduledStopovers != null && !this.scheduledStopovers.isEmpty();
    }

    public String getOperatingLine() {
        Joiner joiner = Joiner.on(DASH).skipNulls();
        return hasStopovers() ?
                joiner.join(scheduledStopovers.getFirst().getAirportIATA(), scheduledStopovers.getLast().getAirportIATA()) :
                joiner.join(departureAirportIATA, arrivalAirportIATA);
    }

    public String getRoutePattern() {
        Joiner joiner = Joiner.on(DASH).skipNulls();
        if (hasStopovers()) {
            List<String> airportIatas = scheduledStopovers.stream()
                    .map(ScheduledStopover::getAirportIATA)
                    .toList();
            return joiner.join(airportIatas);
        }
        return joiner.join(departureAirportIATA, arrivalAirportIATA);
    }

    public String getStopTimesPattern() {
        CharMatcher charMatcher = CharMatcher.anyOf(":Z");
        Joiner dashJoiner = Joiner.on(DASH).skipNulls();

        if (hasStopovers()) {
            Joiner emptyJoiner = Joiner.on(EMPTY).skipNulls();
            List<String> stopTimes = scheduledStopovers.stream()
                    .map(stop -> emptyJoiner.join(stop.getArrivalTime(), stop.getDepartureTime()))
                    .toList();
            return charMatcher.removeFrom(dashJoiner.join(stopTimes));
        }

        return charMatcher.removeFrom(dashJoiner.join(timeOfDeparture, timeOfArrival));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduledFlight that = (ScheduledFlight) o;
        return Objects.equal(flightId, that.flightId) &&
                Objects.equal(airlineIATA, that.airlineIATA) &&
                Objects.equal(airlineName, that.airlineName) &&
                Objects.equal(airlineFlightId, that.airlineFlightId) &&
                Objects.equal(departureAirportIATA, that.departureAirportIATA) &&
                Objects.equal(arrivalAirportIATA, that.arrivalAirportIATA) &&
                Objects.equal(departureAirportName, that.departureAirportName) &&
                Objects.equal(arrivalAirportName, that.arrivalAirportName) &&
                Objects.equal(dateOfOperation, that.dateOfOperation) &&
                Objects.equal(timeOfDeparture, that.timeOfDeparture) &&
                Objects.equal(timeOfArrival, that.timeOfArrival) &&
                Objects.equal(lineDesignation, that.lineDesignation) &&
                Objects.equal(stopsDesignation, that.stopsDesignation) &&
                Objects.equal(timesDesignation, that.timesDesignation) &&
                Objects.equal(scheduledStopovers, that.scheduledStopovers);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(flightId, airlineIATA, airlineName, airlineFlightId, departureAirportIATA,
                arrivalAirportIATA, departureAirportName, arrivalAirportName, dateOfOperation, timeOfDeparture,
                timeOfArrival, lineDesignation, stopsDesignation, timesDesignation, scheduledStopovers);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("flightId", flightId)
                .add("airlineIATA", airlineIATA)
                .add("airlineName", airlineName)
                .add("airlineFlightId", airlineFlightId)
                .add("departureAirportIATA", departureAirportIATA)
                .add("arrivalAirportIATA", arrivalAirportIATA)
                .add("departureAirportName", departureAirportName)
                .add("arrivalAirportName", arrivalAirportName)
                .add("dateOfOperation", dateOfOperation)
                .add("timeOfDeparture", timeOfDeparture)
                .add("timeOfArrival", timeOfArrival)
                .add("lineDesignation", lineDesignation)
                .add("stopsDesignation", stopsDesignation)
                .add("timesDesignation", timesDesignation)
                .add("scheduledStopovers", scheduledStopovers)
                .toString();
    }
}
