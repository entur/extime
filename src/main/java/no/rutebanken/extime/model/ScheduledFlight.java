package no.rutebanken.extime.model;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static no.rutebanken.extime.Constants.DASH;
import static no.rutebanken.extime.Constants.EMPTY;

/**
 * A potentially multi-leg flight from a departure airport to a destination airport, with any number of layovers in
 * between.
 */
public class ScheduledFlight {

    private String airlineIATA;
    private String airlineFlightId;
    private String departureAirportIATA;
    private String arrivalAirportIATA;
    private String arrivalAirportName;
    private LocalDate dateOfOperation;
    private LocalTime timeOfDeparture;
    private LocalTime timeOfArrival;
    private List<ScheduledStopover> scheduledStopovers;

    public String getAirlineIATA() {
        return airlineIATA;
    }

    public void setAirlineIATA(String airlineIATA) {
        this.airlineIATA = airlineIATA;
    }

    public String getAirlineFlightId() {
        return airlineFlightId;
    }

    public void setAirlineFlightId(String airlineFlightId) {
        this.airlineFlightId = airlineFlightId;
    }

    public void setDepartureAirportIATA(String departureAirportIATA) {
        this.departureAirportIATA = departureAirportIATA;
    }

    public void setArrivalAirportIATA(String arrivalAirportIATA) {
        this.arrivalAirportIATA = arrivalAirportIATA;
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

    public List<ScheduledStopover> getScheduledStopovers() {
        if (scheduledStopovers == null) {
            scheduledStopovers = new ArrayList<>();
        }
        return this.scheduledStopovers;
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
        return Objects.equals(airlineIATA, that.airlineIATA) && Objects.equals(airlineFlightId, that.airlineFlightId) && Objects.equals(departureAirportIATA, that.departureAirportIATA) && Objects.equals(arrivalAirportIATA, that.arrivalAirportIATA) && Objects.equals(arrivalAirportName, that.arrivalAirportName) && Objects.equals(dateOfOperation, that.dateOfOperation) && Objects.equals(timeOfDeparture, that.timeOfDeparture) && Objects.equals(timeOfArrival, that.timeOfArrival) && Objects.equals(scheduledStopovers, that.scheduledStopovers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(airlineIATA, airlineFlightId, departureAirportIATA, arrivalAirportIATA, arrivalAirportName, dateOfOperation, timeOfDeparture, timeOfArrival, scheduledStopovers);
    }

    @Override
    public String toString() {
        return "ScheduledFlight{" +
                "airlineIATA='" + airlineIATA + '\'' +
                ", airlineFlightId='" + airlineFlightId + '\'' +
                ", departureAirportIATA='" + departureAirportIATA + '\'' +
                ", arrivalAirportIATA='" + arrivalAirportIATA + '\'' +
                ", arrivalAirportName='" + arrivalAirportName + '\'' +
                ", dateOfOperation=" + dateOfOperation +
                ", timeOfDeparture=" + timeOfDeparture +
                ", timeOfArrival=" + timeOfArrival +
                ", scheduledStopovers=" + scheduledStopovers +
                '}';
    }
}
