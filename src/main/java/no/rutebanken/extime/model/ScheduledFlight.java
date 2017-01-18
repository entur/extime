package no.rutebanken.extime.model;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.math.BigInteger;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static no.rutebanken.extime.Constants.DASH;
import static no.rutebanken.extime.Constants.EMPTY;

// TODO do cleanup in this class, and remove fields no more needed, that are moved to FlightLineDataSet
public class ScheduledFlight {

    private BigInteger flightId;
    private String airlineIATA;
    private String airlineName;
    private String airlineFlightId;
    private String departureAirportIATA;
    private String arrivalAirportIATA;
    private String departureAirportName;
    private String arrivalAirportName;
    private OffsetTime timeOfDeparture;
    private OffsetTime timeOfArrival;
    private LocalDate dateOfOperation;
    private List<ScheduledStopover> scheduledStopovers;

    // properties marked for data set class
    private AvailabilityPeriod availabilityPeriod;
    private Set<DayOfWeek> weekDaysPattern;

    public BigInteger getFlightId() {
        return flightId;
    }

    public void setFlightId(BigInteger flightId) {
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

    public OffsetTime getTimeOfDeparture() {
        return timeOfDeparture;
    }

    public void setTimeOfDeparture(OffsetTime timeOfDeparture) {
        this.timeOfDeparture = timeOfDeparture;
    }

    public OffsetTime getTimeOfArrival() {
        return timeOfArrival;
    }

    public void setTimeOfArrival(OffsetTime timeOfArrival) {
        this.timeOfArrival = timeOfArrival;
    }

    public LocalDate getDateOfOperation() {
        return dateOfOperation;
    }

    public void setDateOfOperation(LocalDate dateOfOperation) {
        this.dateOfOperation = dateOfOperation;
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

    public AvailabilityPeriod getAvailabilityPeriod() {
        return availabilityPeriod;
    }

    public void setAvailabilityPeriod(AvailabilityPeriod availabilityPeriod) {
        this.availabilityPeriod = availabilityPeriod;
    }

    public Set<DayOfWeek> getWeekDaysPattern() {
        return weekDaysPattern;
    }

    public void setWeekDaysPattern(Set<DayOfWeek> weekDaysPattern) {
        this.weekDaysPattern = weekDaysPattern;
    }

    public boolean hasStopovers() {
        return this.scheduledStopovers != null && this.scheduledStopovers.size() > 0;
    }

    public String getOperatingLine() {
        Joiner joiner = Joiner.on(DASH).skipNulls();
        return hasStopovers() ?
                joiner.join(scheduledStopovers.get(0).getAirportIATA(), scheduledStopovers.get(scheduledStopovers.size() - 1).getAirportIATA()) :
                joiner.join(departureAirportIATA, arrivalAirportIATA);
    }

    public String getRoutePattern() {
        Joiner joiner = Joiner.on(DASH).skipNulls();
        if (hasStopovers()) {
            List<String> airportIatas = scheduledStopovers.stream()
                    .map(ScheduledStopover::getAirportIATA)
                    .collect(Collectors.toList());
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
                    .collect(Collectors.toList());
            return charMatcher.removeFrom(dashJoiner.join(stopTimes));
        }

        return dashJoiner.join(timeOfDeparture, timeOfArrival);
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
                Objects.equal(timeOfDeparture, that.timeOfDeparture) &&
                Objects.equal(timeOfArrival, that.timeOfArrival) &&
                Objects.equal(dateOfOperation, that.dateOfOperation) &&
                Objects.equal(scheduledStopovers, that.scheduledStopovers) &&
                Objects.equal(availabilityPeriod, that.availabilityPeriod) &&
                Objects.equal(weekDaysPattern, that.weekDaysPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(flightId, airlineIATA, airlineName, airlineFlightId, departureAirportIATA,
                arrivalAirportIATA, departureAirportName, arrivalAirportName, timeOfDeparture, timeOfArrival,
                dateOfOperation, scheduledStopovers, availabilityPeriod, weekDaysPattern);
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
                .add("timeOfDeparture", timeOfDeparture)
                .add("timeOfArrival", timeOfArrival)
                .add("dateOfOperation", dateOfOperation)
                .add("scheduledStopovers", scheduledStopovers)
                .add("availabilityPeriod", availabilityPeriod)
                .add("weekDaysPattern", weekDaysPattern)
                .toString();
    }
}
