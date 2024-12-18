package no.rutebanken.extime.util;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import org.apache.commons.lang3.StringUtils;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static no.rutebanken.extime.Constants.COLON;
import static no.rutebanken.extime.Constants.UNDERSCORE;

public class NetexObjectIdCreator {

    private static final Random RANDOM = new Random();

    private NetexObjectIdCreator() {
        // Utility class - should not be instantiated
    }

    // frame structure ids

    public static String createCompositeFrameId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.COMPOSITE_FRAME_KEY, objectId);
    }

    public static String createResourceFrameId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.RESOURCE_FRAME_KEY, objectId);
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

    public static String createTimetabledPassingTimeId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.TIMETABLED_PASSING_TIME_KEY, objectId);
    }

    public static String createAuthorityId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.AUTHORITY_KEY, objectId);
    }

    public static String createOperatorId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.OPERATOR_KEY, objectId);
    }

    public static String createBrandingId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.BRANDING_KEY, objectId);
    }

    public static String createNetworkId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.NETWORK_KEY, objectId);
    }

    public static String createGroupOfLinesId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.GROUP_OF_LINES_KEY, objectId);
    }

    public static String createLineId(String objectIdPrefix, String... parts) {
        String objectId = Joiner.on(UNDERSCORE).skipNulls().join(parts);
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.LINE_KEY, objectId);
    }

    public static String createRouteId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.ROUTE_KEY, objectId);
    }

    public static String createQuayId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.QUAY_KEY, objectId);
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

    public static String createDestinationDisplayId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.DESTINATION_DISPLAY, objectId);
    }

    public static String createDayTypeId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.DAY_TYPE_KEY, objectId);
    }

    public static String createDayTypeAssignmentId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.DAY_TYPE_ASSIGNMENT_KEY, objectId);
    }

    public static String createAvailabilityConditionId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.AVAILABILITY_CONDITION_KEY, objectId);
    }

    public static String createServiceJourneyId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.SERVICE_JOURNEY_KEY, objectId);
    }

    public static String createPassengerStopAssignmentId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.PASSENGER_STOP_ASSIGNMENT_KEY, objectId);
    }

    public static String composeNetexObjectId(String objectIdPrefix, String objectIdType, String objectId) {
        return Joiner.on(":").join(
                objectIdPrefix,
                objectIdType,
                objectId.trim()
        );
    }

    public static String[] generateIdSequence(int totalInSequence) {
        String[] idSequence = new String[totalInSequence];
        AtomicInteger incrementor = new AtomicInteger(1);

        for (int i = 0; i < totalInSequence; i++) {
            idSequence[i] = String.valueOf(incrementor.getAndAdd(1));
        }

        return idSequence;
    }


    public static int generateRandomId(int startInclusive, int endExclusive) {
        return RANDOM.nextInt(endExclusive - startInclusive) + startInclusive;
    }

    public static String hashObjectId(String objectId, int end) {
        int hashcode = 0;

        for (int i = 0; i < objectId.length(); i++) {
            hashcode = (hashcode << 5) - hashcode + objectId.charAt(i);
        }

        String hashedObjectId = String.valueOf((hashcode < 0 ? -hashcode : hashcode));
        return StringUtils.substring(hashedObjectId, 0, end);
    }

    public static String getObjectIdSuffix(String objectId) {
        return Iterables.getLast(Splitter.on(COLON)
                .trimResults()
                .split(objectId));
    }

}
