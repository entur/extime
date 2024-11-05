package no.rutebanken.extime.converter;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import jakarta.annotation.PostConstruct;
import no.rutebanken.extime.config.NetexStaticDataSet;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.util.NetexObjectFactory;
import no.rutebanken.extime.util.NetexObjectIdCreator;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.PassengerStopAssignment;
import org.rutebanken.netex.model.PointProjection;
import org.rutebanken.netex.model.PointRefStructure;
import org.rutebanken.netex.model.Projections_RelStructure;
import org.rutebanken.netex.model.QuayRefStructure;
import org.rutebanken.netex.model.RoutePoint;
import org.rutebanken.netex.model.ScheduledStopPoint;
import org.rutebanken.netex.model.ScheduledStopPointRefStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.extime.Constants.AVINOR_XMLNS;
import static no.rutebanken.extime.Constants.DEFAULT_ID_SEPARATOR;
import static no.rutebanken.extime.Constants.VERSION_ONE;

@Component(value = "netexCommonDataSet")
@DependsOn("netexStaticDataSet")
public class NetexCommonDataSet {

    private static final Logger logger = LoggerFactory.getLogger(NetexCommonDataSet.class);

    @Autowired
    private NetexStaticDataSet netexStaticDataSet;

    @Autowired
    private ObjectFactory objectFactory;

    @Autowired
    private NetexObjectFactory netexObjectFactory;

    private final Map<String, ScheduledStopPoint> stopPointMap = new HashMap<>();
    private final Map<String, PassengerStopAssignment> stopAssignmentMap = new HashMap<>();
    private final Map<String, RoutePoint> routePointMap = new HashMap<>();

    @PostConstruct
    public void init() {
        populateStopPointMap();
        populateStopAssignmentMap();
        populateRoutePointMap();
    }


    private String createQuayId(String iata) {
        return NetexObjectIdCreator.createQuayId(AVINOR_XMLNS, iata.toUpperCase());
    }

    private void populateStopPointMap() {
        Map<String, NetexStaticDataSet.StopPlaceDataSet> stopPlaceDataSets = netexStaticDataSet.getStopPlaces();
        List<AirportIATA> airportIATAS = Lists.newArrayList(AirportIATA.values());
        airportIATAS.sort(Comparator.comparing(Enum::name));

        for (AirportIATA airportIATA : airportIATAS) {
            NetexStaticDataSet.StopPlaceDataSet stopPlaceDataSet = stopPlaceDataSets.get(airportIATA.name().toLowerCase());

            if(stopPlaceDataSet == null) {
                throw new IllegalArgumentException("Unknow airport: " + airportIATA.name().toLowerCase());
            }

            String stopPointId = NetexObjectIdCreator.createStopPointId(AVINOR_XMLNS, airportIATA.name());

            ScheduledStopPoint stopPoint = objectFactory.createScheduledStopPoint()
                    .withVersion(VERSION_ONE)
                    .withId(stopPointId)
                    .withName(objectFactory.createMultilingualString().withValue(stopPlaceDataSet.getName()))
                    .withShortName(objectFactory.createMultilingualString().withValue(airportIATA.name()));

            stopPointMap.put(airportIATA.name(), stopPoint);
        }

        logger.info("map populated with {} stop points", stopPointMap.size());
    }

    private void populateStopAssignmentMap() {
        List<AirportIATA> airportIATAS = Lists.newArrayList(AirportIATA.values());
        airportIATAS.sort(Comparator.comparing(Enum::name));

        int index = 1;
        for (AirportIATA airportIATA : airportIATAS) {
            ScheduledStopPoint scheduledStopPoint = stopPointMap.get(airportIATA.name());
            ScheduledStopPointRefStructure scheduledStopPointRefStruct =
                    netexObjectFactory.createScheduledStopPointRefStructure(scheduledStopPoint.getId(), Boolean.TRUE);

            QuayRefStructure quayRefStruct = netexObjectFactory.createQuayRefStructure(createQuayId(airportIATA.name()));

            String stopPointIdSuffix = StringUtils.split(scheduledStopPoint.getId(), DEFAULT_ID_SEPARATOR)[2];

            String passengerStopAssignmentId = NetexObjectIdCreator.createPassengerStopAssignmentId(
                    AVINOR_XMLNS, String.valueOf(stopPointIdSuffix));

            PassengerStopAssignment stopAssignment = objectFactory.createPassengerStopAssignment()
                    .withVersion(VERSION_ONE)
                    .withOrder(new BigInteger(Integer.toString(index)))
                    .withId(passengerStopAssignmentId)
                    .withScheduledStopPointRef(objectFactory.createScheduledStopPointRef(scheduledStopPointRefStruct))
                  //  .withStopPlaceRef(stopPlaceRefStructure)
                    .withQuayRef(objectFactory.createQuayRef(quayRefStruct));
            stopAssignmentMap.put(airportIATA.name(), stopAssignment);
            index++;
        }

        logger.info("map populated with {} stop assignments", stopPointMap.size());
    }

    private void populateRoutePointMap() {
        List<AirportIATA> airportIATAS = Lists.newArrayList(AirportIATA.values());
        airportIATAS.sort(Comparator.comparing(Enum::name));

        for (AirportIATA airportIATA : airportIATAS) {
            ScheduledStopPoint scheduledStopPoint = stopPointMap.get(airportIATA.name());

            PointRefStructure pointRefStruct = netexObjectFactory.createPointRefStructure(scheduledStopPoint.getId(), Boolean.TRUE);

            String stopPointIdSuffix = StringUtils.split(scheduledStopPoint.getId(), DEFAULT_ID_SEPARATOR)[2];
            String pointProjectionId = NetexObjectIdCreator.createPointProjectionId(AVINOR_XMLNS, stopPointIdSuffix);

            PointProjection pointProjection = objectFactory.createPointProjection()
                    .withVersion(VERSION_ONE)
                    .withId(pointProjectionId)
                    .withProjectedPointRef(pointRefStruct);

            Projections_RelStructure projections = objectFactory.createProjections_RelStructure()
                    .withProjectionRefOrProjection(objectFactory.createPointProjection(pointProjection));

            String routePointId = NetexObjectIdCreator.createRoutePointId(AVINOR_XMLNS, stopPointIdSuffix);

            RoutePoint routePoint = objectFactory.createRoutePoint()
                    .withVersion(VERSION_ONE)
                    .withId(routePointId)
                    .withProjections(projections);

            routePointMap.put(airportIATA.name(), routePoint);
        }

        logger.info("map populated with {} route points", routePointMap.size());
    }

    public Map<String, ScheduledStopPoint> getStopPointMap() {
        return stopPointMap;
    }

    public Map<String, PassengerStopAssignment> getStopAssignmentMap() {
        return stopAssignmentMap;
    }

    public Map<String, RoutePoint> getRoutePointMap() {
        return routePointMap;
    }

}
