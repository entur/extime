package no.rutebanken.extime.util;

import com.google.common.base.Joiner;
import org.apache.commons.lang3.RandomUtils;

import java.util.concurrent.atomic.AtomicInteger;

public class NetexObjectIdCreator {

    // frame structure ids

    public static String createCompositeFrameId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.COMPOSITE_FRAME_KEY, objectId);
    }

    public static String createResourceFrameId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.RESOURCE_FRAME_KEY, objectId);
    }

    public static String createSiteFrameId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.SITE_FRAME_KEY, objectId);
    }

    public static String createServiceFrameId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.SERVICE_FRAME_KEY, objectId);
    }

    public static String createServiceCalendarFrameId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.SERVICE_CALENDAR_FRAME_KEY, objectId);
    }

    public static String createTimetableFrameId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.TIMETABLE_FRAME_KEY, objectId);
    }

    // entity ids

    public static String createAuthorityId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.AUTHORITY_KEY, objectId);
    }

    public static String createOperatorId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.OPERATOR_KEY, objectId);
    }

    public static String createNetworkId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.NETWORK_KEY, objectId);
    }

    public static String createLineId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.LINE_KEY, objectId);
    }

    public static String createRouteId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.ROUTE_KEY, objectId);
    }

    public static String createStopPlaceId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.STOP_PLACE_KEY, objectId);
    }

    public static String createStopPointId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.STOP_POINT_KEY, objectId);
    }

    public static String createPointProjectionId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.POINT_PROJECTION_KEY, objectId);
    }

    public static String createRoutePointId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.ROUTE_POINT_KEY, objectId);
    }

    public static String createPointOnRouteId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.POINT_ON_ROUTE_KEY, objectId);
    }

    public static String createJourneyPatternId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.JOURNEY_PATTERN_KEY, objectId);
    }

    public static String createStopPointInJourneyPatternId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.STOP_POINT_IN_JOURNEY_PATTERN_KEY, objectId);
    }

    public static String createDayTypeId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.DAY_TYPE_KEY, objectId);
    }

    public static String createAvailabilityConditionId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.AVAILABILITY_CONDITION_KEY, objectId);
    }

    public static String createServiceJourneyId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.SERVICE_JOURNEY_KEY, objectId);
    }

    public static String composeNetexObjectId(String objectIdPrefix, String objectIdType, String objectId) {
        return Joiner.on(":").join(
                objectIdPrefix,
                objectIdType,
                objectId.trim()
        );
    }

    public static String[] generateIdSequence(int startInclusive, int endExclusive, int totalInSequence) {
        String[] idSequence = new String[totalInSequence];
        int idRangeBase = generateRandomId(startInclusive, endExclusive);
        AtomicInteger incrementor = new AtomicInteger(1);

        for (int i = 0; i < totalInSequence; i++) {
            idSequence[i] = String.format("%d%d", idRangeBase, incrementor.getAndAdd(1));
        }

        return idSequence;
    }

    public static int generateRandomId(int startInclusive, int endExclusive) {
        return RandomUtils.nextInt(startInclusive, endExclusive);
    }

}