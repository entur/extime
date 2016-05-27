package no.rutebanken.extime.model;

import java.math.BigInteger;
import java.time.LocalTime;
import java.time.ZonedDateTime;

public class ScheduledDirectFlight {

    private BigInteger flightId;
    private String airlineIATA;
    private String airlineFlightId;
    private ZonedDateTime dateOfOperation;
    private String departureAirportIATA;
    private String arrivalAirportIATA;
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

    public ZonedDateTime getDateOfOperation() {
        return dateOfOperation;
    }

    public void setDateOfOperation(ZonedDateTime dateOfOperation) {
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
        sb.append(", timeOfDeparture=").append(timeOfDeparture);
        sb.append(", timeOfArrival=").append(timeOfArrival);
        sb.append('}');
        return sb.toString();
    }
}
