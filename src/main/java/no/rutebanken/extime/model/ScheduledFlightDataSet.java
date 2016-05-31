package no.rutebanken.extime.model;

import java.util.ArrayList;
import java.util.List;

public class ScheduledFlightDataSet {

    private List<ScheduledDirectFlight> scheduledDirectFlights;
    private List<ScheduledStopoverFlight> scheduledStopoverFlights;

    public List<ScheduledDirectFlight> getScheduledDirectFlights() {
        if (scheduledDirectFlights == null) {
            scheduledDirectFlights = new ArrayList<>();
        }
        return this.scheduledDirectFlights;
    }

    public List<ScheduledStopoverFlight> getScheduledStopoverFlights() {
        if (scheduledStopoverFlights == null) {
            scheduledStopoverFlights = new ArrayList<>();
        }
        return this.scheduledStopoverFlights;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScheduledFlightDataSet that = (ScheduledFlightDataSet) o;

        if (!scheduledDirectFlights.equals(that.scheduledDirectFlights)) return false;
        return scheduledStopoverFlights.equals(that.scheduledStopoverFlights);

    }

    @Override
    public int hashCode() {
        int result = scheduledDirectFlights.hashCode();
        result = 31 * result + scheduledStopoverFlights.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ScheduledFlightDataSet{");
        sb.append("scheduledDirectFlights=").append(scheduledDirectFlights);
        sb.append(", scheduledStopoverFlights=").append(scheduledStopoverFlights);
        sb.append('}');
        return sb.toString();
    }
}
