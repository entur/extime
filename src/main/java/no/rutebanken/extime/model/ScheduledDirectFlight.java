package no.rutebanken.extime.model;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;

public class ScheduledDirectFlight {

    private BigInteger flightId;
    private String airlineIATA;
    private String airlineFlightId;
    private LocalDate dateOfOperation;
    private String departureAirportIATA;
    private String arrivalAirportIATA;
    private String departureAirportName;
    private String arrivalAirportName;
    private LocalTime timeOfDeparture;
    private LocalTime timeOfArrival;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScheduledDirectFlight that = (ScheduledDirectFlight) o;

        if (!flightId.equals(that.flightId)) return false;
        if (!airlineIATA.equals(that.airlineIATA)) return false;
        if (!airlineFlightId.equals(that.airlineFlightId)) return false;
        if (!dateOfOperation.equals(that.dateOfOperation)) return false;
        if (!departureAirportIATA.equals(that.departureAirportIATA)) return false;
        if (!arrivalAirportIATA.equals(that.arrivalAirportIATA)) return false;
        if (!departureAirportName.equals(that.departureAirportName)) return false;
        if (!arrivalAirportName.equals(that.arrivalAirportName)) return false;
        if (!timeOfDeparture.equals(that.timeOfDeparture)) return false;
        return timeOfArrival.equals(that.timeOfArrival);

    }

    @Override
    public int hashCode() {
        int result = flightId.hashCode();
        result = 31 * result + airlineIATA.hashCode();
        result = 31 * result + airlineFlightId.hashCode();
        result = 31 * result + dateOfOperation.hashCode();
        result = 31 * result + departureAirportIATA.hashCode();
        result = 31 * result + arrivalAirportIATA.hashCode();
        result = 31 * result + departureAirportName.hashCode();
        result = 31 * result + arrivalAirportName.hashCode();
        result = 31 * result + timeOfDeparture.hashCode();
        result = 31 * result + timeOfArrival.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ScheduledDirectFlight{");
        sb.append("flightId=").append(flightId);
        sb.append(", airlineIATA='").append(airlineIATA).append('\'');
        sb.append(", airlineFlightId='").append(airlineFlightId).append('\'');
        sb.append(", dateOfOperation=").append(dateOfOperation);
        sb.append(", departureAirportIATA='").append(departureAirportIATA).append('\'');
        sb.append(", arrivalAirportIATA='").append(arrivalAirportIATA).append('\'');
        sb.append(", departureAirportName='").append(departureAirportName).append('\'');
        sb.append(", arrivalAirportName='").append(arrivalAirportName).append('\'');
        sb.append(", timeOfDeparture=").append(timeOfDeparture);
        sb.append(", timeOfArrival=").append(timeOfArrival);
        sb.append('}');
        return sb.toString();
    }
}
