package no.rutebanken.extime.model;

import java.math.BigInteger;
import java.time.LocalTime;

public class ScheduledDirectFlight extends ScheduledFlight {

    private BigInteger flightId;
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

    @Override
    public String getDepartureAirportIATA() {
        return departureAirportIATA;
    }

    public void setDepartureAirportIATA(String departureAirportIATA) {
        this.departureAirportIATA = departureAirportIATA;
    }

    @Override
    public String getArrivalAirportIATA() {
        return arrivalAirportIATA;
    }

    public void setArrivalAirportIATA(String arrivalAirportIATA) {
        this.arrivalAirportIATA = arrivalAirportIATA;
    }

    @Override
    public String getDepartureAirportName() {
        return departureAirportName;
    }

    public void setDepartureAirportName(String departureAirportName) {
        this.departureAirportName = departureAirportName;
    }

    @Override
    public String getArrivalAirportName() {
        return arrivalAirportName;
    }

    public void setArrivalAirportName(String arrivalAirportName) {
        this.arrivalAirportName = arrivalAirportName;
    }

    @Override
    public LocalTime getTimeOfDeparture() {
        return timeOfDeparture;
    }

    public void setTimeOfDeparture(LocalTime timeOfDeparture) {
        this.timeOfDeparture = timeOfDeparture;
    }

    @Override
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
        if (!super.equals(o)) return false;

        ScheduledDirectFlight that = (ScheduledDirectFlight) o;

        if (flightId != null ? !flightId.equals(that.flightId) : that.flightId != null) return false;
        if (!departureAirportIATA.equals(that.departureAirportIATA)) return false;
        if (!arrivalAirportIATA.equals(that.arrivalAirportIATA)) return false;
        if (!departureAirportName.equals(that.departureAirportName)) return false;
        if (!arrivalAirportName.equals(that.arrivalAirportName)) return false;
        if (!timeOfDeparture.equals(that.timeOfDeparture)) return false;
        return timeOfArrival.equals(that.timeOfArrival);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (flightId != null ? flightId.hashCode() : 0);
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