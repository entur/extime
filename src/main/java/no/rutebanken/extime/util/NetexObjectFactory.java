package no.rutebanken.extime.util;

import no.rutebanken.extime.config.NetexStaticDataSet.StopPlaceDataSet;
import org.rutebanken.netex.model.*;

import javax.xml.bind.JAXBElement;
import java.math.BigInteger;

public class NetexObjectFactory {

    private static final String DEFAULT_ID_PREFIX = "AVI";
    private static final String DEFAULT_VERSION_NUMBER = "1";

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

    public static PointOnRoute createPointOnRoute(String objectId, String stopPointRef) {
        String pointOnRouteId = NetexObjectIdCreator.createPointOnRouteId(DEFAULT_ID_PREFIX, objectId);

        RoutePointRefStructure routePointRefStruct = objectFactory.createRoutePointRefStructure()
                // .withVersion(DEFAULT_VERSION_TEXT) // TODO: temp. disableD to prevent id check, enable and fix
                .withRef(stopPointRef);
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

    public static ScheduledStopPointRefStructure createScheduledStopPointRefStructure(String stopPointId) {
        return objectFactory.createScheduledStopPointRefStructure()
                .withVersion(DEFAULT_VERSION_NUMBER)
                .withRef(stopPointId);
    }

    public static MultilingualString createMultilingualString(String value) {
        return objectFactory.createMultilingualString().withValue(value);
    }

}
