package no.rutebanken.extime.model;

import com.google.common.base.Joiner;

import java.math.BigInteger;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

public class ScheduledStopoverFlight extends ScheduledFlight {

    private BigInteger id; // or serie of ids
    private String routeString; // could also be implemented in separate getter or toString, like "OSL-HOV-SOG-BGO"

    private List<ScheduledStopover> scheduledStopovers;

    public BigInteger getId() {
        return id;
    }

    public void setId(BigInteger id) {
        this.id = id;
    }

    public String getRouteString() {
        return routeString;
    }

    public void setRouteString(String routeString) {
        this.routeString = routeString;
    }

    public List<ScheduledStopover> getScheduledStopovers() {
        if (scheduledStopovers == null) {
            scheduledStopovers = new ArrayList<>();
        }
        return this.scheduledStopovers;
    }

    public String getRoutePath() {
        List<String> airportIATAs = scheduledStopovers.stream()
                .map(ScheduledStopover::getAirportName)
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
        return Joiner.on("-").join(airportIATAs);
    }

    @Override
    public String getDepartureAirportIATA() {
        return scheduledStopovers.get(0).getAirportIATA();
    }

    @Override
    public String getArrivalAirportIATA() {
        return scheduledStopovers.get(scheduledStopovers.size() - 1).getAirportIATA();
    }

    @Override
    public String getDepartureAirportName() {
        return scheduledStopovers.get(0).getAirportName();
    }

    @Override
    public String getArrivalAirportName() {
        return scheduledStopovers.get(scheduledStopovers.size() - 1).getAirportName();
    }

    @Override
    public LocalTime getTimeOfDeparture() {
        return scheduledStopovers.get(0).getDepartureTime();
    }

    @Override
    public LocalTime getTimeOfArrival() {
        return scheduledStopovers.get(scheduledStopovers.size() - 1).getArrivalTime();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ScheduledStopoverFlight that = (ScheduledStopoverFlight) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (routeString != null ? !routeString.equals(that.routeString) : that.routeString != null) return false;
        return scheduledStopovers != null ? scheduledStopovers.equals(that.scheduledStopovers) : that.scheduledStopovers == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (routeString != null ? routeString.hashCode() : 0);
        result = 31 * result + (scheduledStopovers != null ? scheduledStopovers.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ScheduledStopoverFlight{");
        sb.append("id=").append(id);
        sb.append(", routeString='").append(routeString).append('\'');
        sb.append(", scheduledStopovers=").append(scheduledStopovers);
        sb.append('}');
        return sb.toString();
    }
}
