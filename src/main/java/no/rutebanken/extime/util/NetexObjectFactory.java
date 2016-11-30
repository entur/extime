package no.rutebanken.extime.util;

import no.rutebanken.extime.config.NetexStaticDataSet.StopPlaceDataSet;
import org.rutebanken.netex.model.*;

import javax.xml.bind.JAXBElement;
import java.math.BigInteger;

public class NetexObjectFactory {

    private static final String DEFAULT_ID_PREFIX = "AVI";
    static final String DEFAULT_VERSION_NUMBER = "1";

    private static ObjectFactory objectFactory = new ObjectFactory();

    public static StopPlace createStopPlace(String objectId, String airportIATA, StopPlaceDataSet stopPlaceDataSet, String srsName) {
        String stopPlaceId = NetexObjectIdCreator.createStopPlaceId(DEFAULT_ID_PREFIX, objectId);

        LocationStructure locationStruct = objectFactory.createLocationStructure()
                .withSrsName(srsName)
                .withLatitude(stopPlaceDataSet.getLocation().getLatitude())
                .withLongitude(stopPlaceDataSet.getLocation().getLongitude());

        SimplePoint_VersionStructure pointStruct = objectFactory.createSimplePoint_VersionStructure()
                .withLocation(locationStruct);

        return objectFactory.createStopPlace()
                .withVersion(DEFAULT_VERSION_NUMBER)
                .withId(stopPlaceId)
                .withName(createMultilingualString(stopPlaceDataSet.getName()))
                .withShortName(createMultilingualString(airportIATA))
                .withCentroid(pointStruct)
                .withStopPlaceType(StopTypeEnumeration.AIRPORT);
    }

    // TODO consider the id generation to moved to converter level instead, for more precise control, use a uniform way of doing it
    public static PointOnRoute createPointOnRoute(String objectId, String stopPointId) {
        String pointOnRouteId = NetexObjectIdCreator.createPointOnRouteId(DEFAULT_ID_PREFIX, objectId);
        RoutePointRefStructure routePointRefStruct = createRoutePointRefStructure(stopPointId);
        JAXBElement<RoutePointRefStructure> routePointRefStructElement = objectFactory.createRoutePointRef(routePointRefStruct);

        return objectFactory.createPointOnRoute()
                .withVersion(DEFAULT_VERSION_NUMBER)
                .withId(pointOnRouteId)
                .withPointRef(routePointRefStructElement);
    }

    public static StopPointInJourneyPattern createStopPointInJourneyPattern(String objectId, BigInteger orderIndex, String stopPointId) {
        String stopPointInJourneyPatternId = NetexObjectIdCreator.createStopPointInJourneyPatternId(DEFAULT_ID_PREFIX, objectId);
        ScheduledStopPointRefStructure stopPointRefStruct = createScheduledStopPointRefStructure(stopPointId);
        JAXBElement<ScheduledStopPointRefStructure> stopPointRefStructElement = objectFactory.createScheduledStopPointRef(stopPointRefStruct);

        return objectFactory.createStopPointInJourneyPattern()
                .withVersion(DEFAULT_VERSION_NUMBER)
                .withId(stopPointInJourneyPatternId)
                .withOrder(orderIndex)
                .withScheduledStopPointRef(stopPointRefStructElement);
    }

    // TODO find out how to best handle incoming departure and arrival times, disabled for now, caller responsible to set
    public static TimetabledPassingTime createTimetabledPassingTime(String stopPointInJourneyPatternId) {
        StopPointInJourneyPatternRefStructure stopPointInJourneyPatternRefStruct =
                createStopPointInJourneyPatternRefStructure(stopPointInJourneyPatternId);

        JAXBElement<StopPointInJourneyPatternRefStructure> stopPointInJourneyPatternRefStructElement = objectFactory
                .createStopPointInJourneyPatternRef(stopPointInJourneyPatternRefStruct);

        return objectFactory.createTimetabledPassingTime()
                .withPointInJourneyPatternRef(stopPointInJourneyPatternRefStructElement);
    }

    public static MultilingualString createMultilingualString(String value) {
        return objectFactory.createMultilingualString().withValue(value);
    }

    // reference structures creation

    public static OperatorRefStructure createOperatorRefStructure(String operatorId) {
        return objectFactory.createOperatorRefStructure()
                .withVersion(DEFAULT_VERSION_NUMBER)
                .withRef(operatorId);
    }

    public static RouteRefStructure createRouteRefStructure(String routeId) {
        return objectFactory.createRouteRefStructure()
                .withVersion(DEFAULT_VERSION_NUMBER)
                .withRef(routeId);
    }

    public static StopPlaceRefStructure createStopPlaceRefStructure(String stopPlaceId) {
        return objectFactory.createStopPlaceRefStructure()
                .withVersion(DEFAULT_VERSION_NUMBER)
                .withRef(stopPlaceId);
    }

    public static ScheduledStopPointRefStructure createScheduledStopPointRefStructure(String stopPointId) {
        return objectFactory.createScheduledStopPointRefStructure()
                .withVersion(DEFAULT_VERSION_NUMBER)
                .withRef(stopPointId);
    }

    public static StopPointInJourneyPatternRefStructure createStopPointInJourneyPatternRefStructure(String stopPointInJourneyPatternId) {
        return objectFactory.createStopPointInJourneyPatternRefStructure()
                .withVersion(DEFAULT_VERSION_NUMBER)
                .withRef(stopPointInJourneyPatternId);
    }

    public static PointRefStructure createPointRefStructure(String stopPointId) {
        return objectFactory.createPointRefStructure()
                .withVersion(DEFAULT_VERSION_NUMBER)
                .withRef(stopPointId);
    }

    public static RoutePointRefStructure createRoutePointRefStructure(String stopPointId) {
        return objectFactory.createRoutePointRefStructure()
                .withVersion(DEFAULT_VERSION_NUMBER)
                .withRef(stopPointId);
    }

    public static DayTypeRefStructure createDayTypeRefStructure(String dayTypeId) {
        return objectFactory.createDayTypeRefStructure()
                .withVersion(DEFAULT_VERSION_NUMBER)
                .withRef(dayTypeId);
    }

}
