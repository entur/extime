package no.rutebanken.extime;

public final class Constants {

    private Constants() {}

    // common application constants
    public static final String EMPTY = "";
    public static final String DASH = "-";
    public static final String UNDERSCORE = "_";
    public static final String COLON = ":";
    public static final String DEFAULT_ZONE_ID = "Europe/Oslo";
    public static final String DEFAULT_LANGUAGE = "no";
    public static final int DEFAULT_START_INCLUSIVE = 1111111;
    public static final int DEFAULT_END_EXCLUSIVE = 8888888;

    // date/period specific constants
    public static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd";
    public static final String DAY_TYPE_PATTERN = "MMM_EEE_dd";

    // netex specific constants
    public static final String NETEX_PROFILE_VERSION = "1.13:NO-NeTEx-networktimetable:1.3";
    public static final String VERSION_ONE = "1";

    public static final String NSR_XMLNS = "NSR";
    public static final String NSR_XMLNSURL = "http://www.rutebanken.org/ns/nsr";
    
    public static final String AVINOR_XMLNS = "AVI";
    public static final String AVINOR_XMLNSURL = "http://www.rutebanken.org/ns/avi";
    
    public static final String DEFAULT_COORDINATE_SYSTEM = "WGS84";
    public static final String DEFAULT_ID_SEPARATOR = ":";

}
