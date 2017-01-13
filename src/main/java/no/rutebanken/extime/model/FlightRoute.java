package no.rutebanken.extime.model;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.util.List;

public class FlightRoute {

    private String routeDesignation;
    private String routeName;
    private List<String> routePointsInSequence;

    public FlightRoute(String routeDesignation, String routeName) {
        this.routeDesignation = routeDesignation;
        this.routeName = routeName;
    }

    public String getRouteDesignation() {
        return routeDesignation;
    }

    public void setRouteDesignation(String routeDesignation) {
        this.routeDesignation = routeDesignation;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    // TODO maybe we could create the list at the first access
    public List<String> getRoutePointsInSequence() {
        return routePointsInSequence;
    }

    public void setRoutePointsInSequence(List<String> routePointsInSequence) {
        this.routePointsInSequence = routePointsInSequence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlightRoute that = (FlightRoute) o;
        return Objects.equal(routeDesignation, that.routeDesignation) &&
                Objects.equal(routeName, that.routeName) &&
                Objects.equal(routePointsInSequence, that.routePointsInSequence);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(routeDesignation, routeName, routePointsInSequence);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("routeDesignation", routeDesignation)
                .add("routeName", routeName)
                .add("routePointsInSequence", routePointsInSequence)
                .toString();
    }
}
