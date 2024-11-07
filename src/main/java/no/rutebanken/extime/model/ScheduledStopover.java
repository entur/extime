package no.rutebanken.extime.model;

import java.time.LocalTime;
import java.util.Objects;

public class ScheduledStopover {

    private String airportIATA;
    private LocalTime arrivalTime;
    private LocalTime departureTime;

    public String getAirportIATA() {
        return airportIATA;
    }

    public void setAirportIATA(String airportIATA) {
        this.airportIATA = airportIATA;
    }

    public LocalTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalTime departureTime) {
        this.departureTime = departureTime;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduledStopover that = (ScheduledStopover) o;
        return Objects.equals(airportIATA, that.airportIATA) && Objects.equals(arrivalTime, that.arrivalTime) && Objects.equals(departureTime, that.departureTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(airportIATA, arrivalTime, departureTime);
    }

    @Override
    public String toString() {
        return "ScheduledStopover{" +
                "airportIATA='" + airportIATA + '\'' +
                ", arrivalTime=" + arrivalTime +
                ", departureTime=" + departureTime +
                '}';
    }
}
