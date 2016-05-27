package no.rutebanken.extime.model;

import java.math.BigInteger;
import java.time.LocalTime;

public class ScheduledStopover {

    private BigInteger id;
    private String airportIATA;
    private String airportName;
    private LocalTime arrivalTime;
    private LocalTime departureTime;
    private Boolean forAlighting;
    private Boolean forBoarding;
    private Integer order;

    public BigInteger getId() {
        return id;
    }

    public void setId(BigInteger id) {
        this.id = id;
    }

    public String getAirportIATA() {
        return airportIATA;
    }

    public void setAirportIATA(String airportIATA) {
        this.airportIATA = airportIATA;
    }

    public String getAirportName() {
        return airportName;
    }

    public void setAirportName(String airportName) {
        this.airportName = airportName;
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

    public Boolean getForAlighting() {
        return forAlighting;
    }

    public void setForAlighting(Boolean forAlighting) {
        this.forAlighting = forAlighting;
    }

    public Boolean getForBoarding() {
        return forBoarding;
    }

    public void setForBoarding(Boolean forBoarding) {
        this.forBoarding = forBoarding;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScheduledStopover that = (ScheduledStopover) o;

        if (!id.equals(that.id)) return false;
        if (!airportIATA.equals(that.airportIATA)) return false;
        if (!airportName.equals(that.airportName)) return false;
        if (!arrivalTime.equals(that.arrivalTime)) return false;
        if (!departureTime.equals(that.departureTime)) return false;
        if (!forAlighting.equals(that.forAlighting)) return false;
        if (!forBoarding.equals(that.forBoarding)) return false;
        return order.equals(that.order);

    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + airportIATA.hashCode();
        result = 31 * result + airportName.hashCode();
        result = 31 * result + arrivalTime.hashCode();
        result = 31 * result + departureTime.hashCode();
        result = 31 * result + forAlighting.hashCode();
        result = 31 * result + forBoarding.hashCode();
        result = 31 * result + order.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ScheduledStopover{");
        sb.append("id=").append(id);
        sb.append(", airportIATA='").append(airportIATA).append('\'');
        sb.append(", airportName='").append(airportName).append('\'');
        sb.append(", arrivalTime=").append(arrivalTime);
        sb.append(", departureTime=").append(departureTime);
        sb.append(", forAlighting=").append(forAlighting);
        sb.append(", forBoarding=").append(forBoarding);
        sb.append(", order=").append(order);
        sb.append('}');
        return sb.toString();
    }
}
