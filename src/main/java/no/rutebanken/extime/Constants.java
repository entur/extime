package no.rutebanken.extime;

public final class Constants {

    private Constants() {}

    // common application constants

    public static final String DEFAULT_ZONE_ID = "UTC";
    public static final String DEFAULT_LANGUAGE = "no";
    public static final int DEFAULT_START_INCLUSIVE = 1111111;
    public static final int DEFAULT_END_EXCLUSIVE = 8888888;

    // netex specific constants

    public static final String NETEX_PROFILE_VERSION = "1.04:NO-NeTEx-networktimetable:1.0";
    public static final String VERSION_ONE = "1";
    public static final String NSR_AUTHORITY_ID = "NSR";
    public static final String AVINOR_AUTHORITY_ID = "AVI";
    public static final String NSR_XMLNS_URL = "http://www.rutebanken.org/ns/nsr";
    public static final String AVINOR_XMLNS_URL = "http://www.rutebanken.org/ns/avi";
    public static final String DEFAULT_COORDINATE_SYSTEM = "WGS84";
    public static final String DEFAULT_ID_SEPARATOR = ":";

}
