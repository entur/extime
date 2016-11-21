package no.rutebanken.extime.util;

import com.google.common.base.Joiner;

public class NetexObjectIdCreator {

    public static String createAuthorityId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.AUTHORITY_KEY, objectId);
    }

    public static String createOperatorId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.OPERATOR_KEY, objectId);
    }

    public static String createNetworkId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.NETWORK_KEY, objectId);
    }

    public static String createStopPlaceId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.STOP_PLACE_KEY, objectId);
    }

    public static String createRouteId(String objectIdPrefix, String objectId) {
        return NetexObjectIdCreator.composeNetexObjectId(objectIdPrefix, NetexObjectIdTypes.ROUTE_KEY, objectId);
    }

    public static String composeNetexObjectId(String objectIdPrefix, String objectIdType, String objectId) {
        return Joiner.on(":").join(
                objectIdPrefix,
                objectIdType,
                objectId.trim()
        );
    }

}
