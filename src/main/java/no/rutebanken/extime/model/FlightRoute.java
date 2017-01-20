package no.rutebanken.extime.model;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;

import java.util.Collections;
import java.util.List;

import static no.rutebanken.extime.Constants.DASH;

public class FlightRoute {

    private String routeDesignation;
    private String routeName;

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

    public List<String> getRoutePointsInSequence() {
        if (routeDesignation != null) {
            return Splitter.on(DASH)
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToList(routeDesignation);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlightRoute that = (FlightRoute) o;
        return Objects.equal(routeDesignation, that.routeDesignation) &&
                Objects.equal(routeName, that.routeName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(routeDesignation, routeName);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("routeDesignation", routeDesignation)
                .add("routeName", routeName)
                .toString();
    }
}
