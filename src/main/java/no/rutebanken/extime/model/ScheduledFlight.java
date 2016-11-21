package no.rutebanken.extime.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.util.Set;

public abstract class ScheduledFlight {

    private String airlineIATA;
    private String airlineName;
    private String airlineFlightId;
    private AvailabilityPeriod availabilityPeriod;
    private LocalDate dateOfOperation;
    private Set<DayOfWeek> weekDaysPattern;

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

    public LocalDate getDateOfOperation() {
        return dateOfOperation;
    }

    public void setDateOfOperation(LocalDate dateOfOperation) {
        this.dateOfOperation = dateOfOperation;
    }

    public Set<DayOfWeek> getWeekDaysPattern() {
        return weekDaysPattern;
    }

    public void setWeekDaysPattern(Set<DayOfWeek> weekDaysPattern) {
        this.weekDaysPattern = weekDaysPattern;
    }

    public abstract String getDepartureAirportIATA();

    public abstract String getArrivalAirportIATA();

    public abstract String getDepartureAirportName();

    public abstract String getArrivalAirportName();

    public abstract OffsetTime getTimeOfDeparture();

    public abstract OffsetTime getTimeOfArrival();

    public AvailabilityPeriod getAvailabilityPeriod() {
        return availabilityPeriod;
    }

    public void setAvailabilityPeriod(AvailabilityPeriod availabilityPeriod) {
        this.availabilityPeriod = availabilityPeriod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScheduledFlight that = (ScheduledFlight) o;

        if (airlineIATA != null ? !airlineIATA.equals(that.airlineIATA) : that.airlineIATA != null) return false;
        if (airlineName != null ? !airlineName.equals(that.airlineName) : that.airlineName != null) return false;
        if (airlineFlightId != null ? !airlineFlightId.equals(that.airlineFlightId) : that.airlineFlightId != null)
            return false;
        if (dateOfOperation != null ? !dateOfOperation.equals(that.dateOfOperation) : that.dateOfOperation != null)
            return false;
        return weekDaysPattern != null ? weekDaysPattern.equals(that.weekDaysPattern) : that.weekDaysPattern == null;

    }

    @Override
    public int hashCode() {
        int result = airlineIATA != null ? airlineIATA.hashCode() : 0;
        result = 31 * result + (airlineName != null ? airlineName.hashCode() : 0);
        result = 31 * result + (airlineFlightId != null ? airlineFlightId.hashCode() : 0);
        result = 31 * result + (dateOfOperation != null ? dateOfOperation.hashCode() : 0);
        result = 31 * result + (weekDaysPattern != null ? weekDaysPattern.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ScheduledFlight{");
        sb.append("airlineIATA='").append(airlineIATA).append('\'');
        sb.append(", airlineName='").append(airlineName).append('\'');
        sb.append(", airlineFlightId='").append(airlineFlightId).append('\'');
        sb.append(", dateOfOperation=").append(dateOfOperation);
        sb.append(", weekDaysPattern=").append(weekDaysPattern);
        sb.append('}');
        return sb.toString();
    }

}
