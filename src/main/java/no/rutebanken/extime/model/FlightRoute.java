package no.rutebanken.extime.model;

import com.google.common.base.Splitter;

import java.util.Collections;
import java.util.List;

import static no.rutebanken.extime.Constants.DASH;

public record FlightRoute(String routeDesignation, String routeName) {

    public List<String> getRoutePointsInSequence() {
        if (routeDesignation != null) {
            return Splitter.on(DASH)
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToList(routeDesignation);
        }
        return Collections.emptyList();
    }

}
