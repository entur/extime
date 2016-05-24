package no.rutebanken.extime.model;

import no.avinor.flydata.xjc.model.airport.AirportName;
import no.avinor.flydata.xjc.model.feed.Flight;

import java.util.List;

public class FlightRouteDataSet {

    private String flightId;
    private String airlineIATA;
    private AirportName departureAirportName;
    private AirportName arrivalAirportName;

    private Flight departureFlight;
    private Flight arrivalFlight;

    private List<Flight> stopovers;

    public String getFlightId() {
        return flightId;
    }

    public void setFlightId(String flightId) {
        this.flightId = flightId;
    }

    public String getAirlineIATA() {
        return airlineIATA;
    }

    public void setAirlineIATA(String airlineIATA) {
        this.airlineIATA = airlineIATA;
    }

    public Flight getDepartureFlight() {
        return departureFlight;
    }

    public void setDepartureFlight(Flight departureFlight) {
        this.departureFlight = departureFlight;
    }

    public Flight getArrivalFlight() {
        return arrivalFlight;
    }

    public void setArrivalFlight(Flight arrivalFlight) {
        this.arrivalFlight = arrivalFlight;
    }

    public List<Flight> getStopovers() {
        return stopovers;
    }

    public void setStopovers(List<Flight> stopovers) {
        this.stopovers = stopovers;
    }

    public AirportName getDepartureAirportName() {
        return departureAirportName;
    }

    public void setDepartureAirportName(AirportName departureAirportName) {
        this.departureAirportName = departureAirportName;
    }

    public AirportName getArrivalAirportName() {
        return arrivalAirportName;
    }

    public void setArrivalAirportName(AirportName arrivalAirportName) {
        this.arrivalAirportName = arrivalAirportName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FlightRouteDataSet that = (FlightRouteDataSet) o;

        if (flightId != null ? !flightId.equals(that.flightId) : that.flightId != null) return false;
        if (airlineIATA != null ? !airlineIATA.equals(that.airlineIATA) : that.airlineIATA != null) return false;
        if (departureAirportName != null ? !departureAirportName.equals(that.departureAirportName) : that.departureAirportName != null)
            return false;
        if (arrivalAirportName != null ? !arrivalAirportName.equals(that.arrivalAirportName) : that.arrivalAirportName != null)
            return false;
        if (departureFlight != null ? !departureFlight.equals(that.departureFlight) : that.departureFlight != null)
            return false;
        if (arrivalFlight != null ? !arrivalFlight.equals(that.arrivalFlight) : that.arrivalFlight != null)
            return false;
        return stopovers != null ? stopovers.equals(that.stopovers) : that.stopovers == null;

    }

    @Override
    public int hashCode() {
        int result = flightId != null ? flightId.hashCode() : 0;
        result = 31 * result + (airlineIATA != null ? airlineIATA.hashCode() : 0);
        result = 31 * result + (departureAirportName != null ? departureAirportName.hashCode() : 0);
        result = 31 * result + (arrivalAirportName != null ? arrivalAirportName.hashCode() : 0);
        result = 31 * result + (departureFlight != null ? departureFlight.hashCode() : 0);
        result = 31 * result + (arrivalFlight != null ? arrivalFlight.hashCode() : 0);
        result = 31 * result + (stopovers != null ? stopovers.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("FlightRouteDataSet{");
        sb.append("flightId='").append(flightId).append('\'');
        sb.append(", airlineIATA='").append(airlineIATA).append('\'');
        sb.append(", departureAirportName=").append(departureAirportName.getName());
        sb.append(", arrivalAirportName=").append(arrivalAirportName.getName());
        sb.append(", departureFlight=").append(departureFlight.getUniqueID());
        sb.append(", arrivalFlight=").append(arrivalFlight.getUniqueID());
        sb.append('}');
        return sb.toString();
    }

}
