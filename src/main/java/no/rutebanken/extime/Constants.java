package no.rutebanken.extime;

public final class Constants {

    private Constants() {}

    // common application constants
    public static final String DASH = "-";
    public static final String EQUIVALENT_SYMBOL = "<=>";
    public static final String DEFAULT_ZONE_ID = "UTC";
    public static final String DEFAULT_LANGUAGE = "no";
    public static final int DEFAULT_START_INCLUSIVE = 1111111;
    public static final int DEFAULT_END_EXCLUSIVE = 8888888;

    // date/period specific constants
    public static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd";
    public static final String OFFSET_MIDNIGHT_UTC = "00:00:00Z";

    // netex specific constants
    public static final String NETEX_PROFILE_VERSION = "1.04:NO-NeTEx-networktimetable:1.0";
    public static final String VERSION_ONE = "1";

    public static final String NSR_XMLNS = "NSR";
    public static final String NSR_XMLNSURL = "http://www.rutebanken.org/ns/nsr";
    
    public static final String AVINOR_XMLNS = "AVI";
    public static final String AVINOR_XMLNSURL = "http://www.rutebanken.org/ns/avi";
    
    public static final String DEFAULT_COORDINATE_SYSTEM = "WGS84";
    public static final String DEFAULT_ID_SEPARATOR = ":";

}
