package no.rutebanken.extime.model;

import com.google.common.base.Joiner;
import no.avinor.flydata.xjc.model.scheduled.Flight;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

public class ScheduledStopoverFlight {

    private BigInteger id; // or serie of ids
    private String flightId;
    private LocalDate dateOfOperation; // or a date range, if flight goes over multiple dates
    private String routeString; // could also be implemented in separate getter or toString, like "OSL-HOV-SOG-BGO"

    private List<ScheduledStopover> scheduledStopovers;

    public BigInteger getId() {
        return id;
    }

    public void setId(BigInteger id) {
        this.id = id;
    }

    public String getFlightId() {
        return flightId;
    }

    public void setFlightId(String flightId) {
        this.flightId = flightId;
    }

    public LocalDate getDateOfOperation() {
        return dateOfOperation;
    }

    public void setDateOfOperation(LocalDate dateOfOperation) {
        this.dateOfOperation = dateOfOperation;
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
                .map(ScheduledStopover::getAirportIATA)
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
        return Joiner.on("-").join(airportIATAs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScheduledStopoverFlight that = (ScheduledStopoverFlight) o;

        if (!id.equals(that.id)) return false;
        if (!flightId.equals(that.flightId)) return false;
        if (!dateOfOperation.equals(that.dateOfOperation)) return false;
        if (!routeString.equals(that.routeString)) return false;
        return scheduledStopovers.equals(that.scheduledStopovers);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + flightId.hashCode();
        result = 31 * result + dateOfOperation.hashCode();
        result = 31 * result + routeString.hashCode();
        result = 31 * result + scheduledStopovers.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ScheduledStopoverFlight{");
        sb.append("id=").append(id);
        sb.append(", flightId='").append(flightId).append('\'');
        sb.append(", dateOfOperation=").append(dateOfOperation);
        sb.append(", routeString='").append(routeString).append('\'');
        sb.append(", scheduledStopovers=").append(scheduledStopovers);
        sb.append('}');
        return sb.toString();
    }
}
