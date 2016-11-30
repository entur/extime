package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.config.NetexStaticDataSet;
import no.rutebanken.extime.config.NetexStaticDataSet.StopPlaceDataSet;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.util.NetexObjectIdCreator;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// TODO refactor and optimize this class
@Component(value = "netexCommonDataSet")
public class NetexCommonDataSet {

    private static final Logger logger = LoggerFactory.getLogger(NetexCommonDataSet.class);

    private AtomicInteger atomicInteger = new AtomicInteger(17733600);

    @Autowired
    private NetexStaticDataSet netexStaticDataSet;

    private Map<String, StopPlace> stopPlaceMap = new HashMap<>();
    private Map<String, ScheduledStopPoint> stopPointMap = new HashMap<>();
    private Map<String, PassengerStopAssignment> stopAssignmentMap = new HashMap<>();
    private Map<String, RoutePoint> routePointMap = new HashMap<>();

    @Bean
    public ObjectFactory objectFactory() {
        return new ObjectFactory();
    }

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
            String stopPlaceId = NetexObjectIdCreator.createStopPlaceId("AVI", airportIATA.name().toUpperCase());
            StopPlaceDataSet stopPlaceDataSet = stopPlaceDataSets.get(airportIATA.name().toLowerCase());

            LocationStructure locationStruct = objectFactory().createLocationStructure()
                    .withSrsName("WGS84")
                    .withLatitude(stopPlaceDataSet.getLocation().getLatitude())
                    .withLongitude(stopPlaceDataSet.getLocation().getLongitude());

            SimplePoint_VersionStructure pointStruct = objectFactory().createSimplePoint_VersionStructure()
                    .withLocation(locationStruct);

            StopPlace stopPlace = objectFactory().createStopPlace()
                    .withVersion("1")
                    .withId(stopPlaceId)
                    .withName(objectFactory().createMultilingualString().withValue(stopPlaceDataSet.getName()))
                    .withShortName(objectFactory().createMultilingualString().withValue(airportIATA.name()))
                    .withCentroid(pointStruct)
                    .withStopPlaceType(StopTypeEnumeration.AIRPORT);

            stopPlaceMap.put(airportIATA.name(), stopPlace);
        }

        logger.info("map populated with {} stop places", stopPlaceMap.size());
    }

    // TODO remember, a scheduled stop point cannot use a randomly generate id suffix, this must be static
    // TODO find a better way to create this id
    private void populateStopPointMap() {
        Map<String, StopPlaceDataSet> stopPlaceDataSets = netexStaticDataSet.getStopPlaces();
        List<AirportIATA> airportIATAS = Lists.newArrayList(AirportIATA.values());
        airportIATAS.sort(Comparator.comparing(Enum::name));

        for (AirportIATA airportIATA : airportIATAS) {
            StopPlaceDataSet stopPlaceDataSet = stopPlaceDataSets.get(airportIATA.name().toLowerCase());
            int stopPointIdSuffix = atomicInteger.addAndGet(1);
            String stopPointId = NetexObjectIdCreator.createStopPointId("AVI", String.valueOf(stopPointIdSuffix));

            ScheduledStopPoint stopPoint = objectFactory().createScheduledStopPoint()
                    .withVersion("any")
                    .withId(stopPointId)
                    .withName(objectFactory().createMultilingualString().withValue(stopPlaceDataSet.getName()))
                    .withShortName(objectFactory().createMultilingualString().withValue(airportIATA.name()));

            stopPointMap.put(airportIATA.name(), stopPoint);
        }

        logger.info("map populated with {} stop points", stopPointMap.size());
    }

    // TODO find out how to handle and set the order attribute for stop assignments
    private void populateStopAssignmentMap() {
        List<AirportIATA> airportIATAS = Lists.newArrayList(AirportIATA.values());
        airportIATAS.sort(Comparator.comparing(Enum::name));

        int index = 1;
        for (AirportIATA airportIATA : airportIATAS) {
            ScheduledStopPoint scheduledStopPoint = stopPointMap.get(airportIATA.name());
            ScheduledStopPointRefStructure scheduledStopPointRef = objectFactory().createScheduledStopPointRefStructure()
                    //.withVersion("any")
                    .withRef(scheduledStopPoint.getId());

            StopPlace stopPlace = stopPlaceMap.get(airportIATA.name());
            StopPlaceRefStructure stopPlaceRef = objectFactory().createStopPlaceRefStructure()
                    //.withVersion("any")
                    .withRef(stopPlace.getId());

            String stopPointIdSuffix = StringUtils.split(scheduledStopPoint.getId(), ":")[2];

            PassengerStopAssignment stopAssignment = objectFactory().createPassengerStopAssignment()
                    .withVersion("any")
                    .withOrder(new BigInteger(Integer.toString(index)))
                    .withId(String.format("AVI:PassengerStopAssignment:%s", stopPointIdSuffix))
                    .withScheduledStopPointRef(scheduledStopPointRef)
                    .withStopPlaceRef(stopPlaceRef);
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

            PointRefStructure pointRefStructure = objectFactory().createPointRefStructure()
                    .withVersion("any")
                    .withRef(scheduledStopPoint.getId());

            String stopPointIdSuffix = StringUtils.split(scheduledStopPoint.getId(), ":")[2];
            String pointProjectionId = NetexObjectIdCreator.createPointProjectionId("AVI", stopPointIdSuffix);

            PointProjection pointProjection = objectFactory().createPointProjection()
                    .withVersion("any")
                    .withId(pointProjectionId)
                    .withProjectedPointRef(pointRefStructure);

            Projections_RelStructure projections = objectFactory().createProjections_RelStructure()
                    .withProjectionRefOrProjection(objectFactory().createPointProjection(pointProjection));

            String routePointId = NetexObjectIdCreator.createRoutePointId("AVI", stopPointIdSuffix);

            RoutePoint routePoint = objectFactory().createRoutePoint()
                    .withVersion("any")
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
