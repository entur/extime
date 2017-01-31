package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.config.NetexStaticDataSet;
import no.rutebanken.extime.config.NetexStaticDataSet.StopPlaceDataSet;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.util.NetexObjectFactory;
import no.rutebanken.extime.util.NetexObjectIdCreator;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.extime.Constants.*;

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

    private Map<String, StopPlace> stopPlaceMap = new HashMap<>();
    private Map<String, Quay> quayMap = new HashMap<>();
    private Map<String, ScheduledStopPoint> stopPointMap = new HashMap<>();
    private Map<String, PassengerStopAssignment> stopAssignmentMap = new HashMap<>();
    private Map<String, RoutePoint> routePointMap = new HashMap<>();

    @PostConstruct
    public void init() {
        populateStopPlaceMap();
        populateStopPointMap();
        populateStopAssignmentMap();
        populateRoutePointMap();
    }

    private void populateStopPlaceMap() {
        Map<String, StopPlaceDataSet> stopPlaceDataSets = netexStaticDataSet.getStopPlaces();
        List<AirportIATA> airportIATAS = Lists.newArrayList(AirportIATA.values());
        airportIATAS.sort(Comparator.comparing(Enum::name));

        for (AirportIATA airportIATA : airportIATAS) {
            String stopPlaceId = NetexObjectIdCreator.createStopPlaceId(AVINOR_XMLNS, airportIATA.name().toUpperCase());
            String quayId = NetexObjectIdCreator.createQuayId(AVINOR_XMLNS, airportIATA.name().toUpperCase());
            StopPlaceDataSet stopPlaceDataSet = stopPlaceDataSets.get(airportIATA.name().toLowerCase());

            LocationStructure locationStruct = objectFactory.createLocationStructure()
                    .withSrsName(DEFAULT_COORDINATE_SYSTEM)
                    .withLatitude(stopPlaceDataSet.getLocation().getLatitude())
                    .withLongitude(stopPlaceDataSet.getLocation().getLongitude());

            SimplePoint_VersionStructure pointStruct = objectFactory.createSimplePoint_VersionStructure()
                    .withLocation(locationStruct);

            Quay quay = objectFactory.createQuay()
                    .withVersion(VERSION_ONE)
                    .withId(quayId)
                    .withCentroid(pointStruct);

            Quays_RelStructure quayRefStruct = objectFactory.createQuays_RelStructure().withQuayRefOrQuay(quay);

            StopPlace stopPlace = objectFactory.createStopPlace()
                    .withVersion(VERSION_ONE)
                    .withId(stopPlaceId)
                    .withName(objectFactory.createMultilingualString().withValue(stopPlaceDataSet.getName()))
                    .withShortName(objectFactory.createMultilingualString().withValue(airportIATA.name()))
                    .withCentroid(pointStruct)
                    .withStopPlaceType(StopTypeEnumeration.AIRPORT)
                    .withQuays(quayRefStruct);

            stopPlaceMap.put(airportIATA.name(), stopPlace);
            quayMap.put(airportIATA.name(), quay);
        }

        logger.info("map populated with {} stop places", stopPlaceMap.size());
    }

    private void populateStopPointMap() {
        Map<String, StopPlaceDataSet> stopPlaceDataSets = netexStaticDataSet.getStopPlaces();
        List<AirportIATA> airportIATAS = Lists.newArrayList(AirportIATA.values());
        airportIATAS.sort(Comparator.comparing(Enum::name));

        for (AirportIATA airportIATA : airportIATAS) {
            StopPlaceDataSet stopPlaceDataSet = stopPlaceDataSets.get(airportIATA.name().toLowerCase());
            String stopPointId = NetexObjectIdCreator.createStopPointId(AVINOR_XMLNS, airportIATA.name().toUpperCase());

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

            QuayRefStructure quayRefStruct = netexObjectFactory.createQuayRefStructure(quayMap.get(airportIATA.name()).getId(), Boolean.TRUE);

            String stopPointIdSuffix = StringUtils.split(scheduledStopPoint.getId(), DEFAULT_ID_SEPARATOR)[2];

            String passengerStopAssignmentId = NetexObjectIdCreator.createPassengerStopAssignmentId(
                    AVINOR_XMLNS, String.valueOf(stopPointIdSuffix));

            PassengerStopAssignment stopAssignment = objectFactory.createPassengerStopAssignment()
                    .withVersion(VERSION_ONE)
                    .withOrder(new BigInteger(Integer.toString(index)))
                    .withId(passengerStopAssignmentId)
                    .withScheduledStopPointRef(scheduledStopPointRefStruct)
                    .withQuayRef(quayRefStruct);
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

    public Map<String, StopPlace> getStopPlaceMap() {
        return stopPlaceMap;
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
