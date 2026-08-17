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
    public static final String DAY_TYPE_PATTERN = "MMM_EEE_dd";

    // netex specific constants
    public static final String NETEX_PROFILE_VERSION = "1.15:NO-NeTEx-networktimetable:1.5";
    public static final String VERSION_ONE = "1";

    public static final String NSR_XMLNS = "NSR";
    public static final String NSR_XMLNSURL = "http://www.rutebanken.org/ns/nsr";

    public static final String AVINOR_XMLNS = "AVI";
    public static final String AVINOR_XMLNSURL = "http://www.rutebanken.org/ns/avi";
    
    public static final String DEFAULT_ID_SEPARATOR = ":";

    // PubSub message attributes on MardukInboundQueue. Marduk matches on these by name, so they are a
    // wire contract, not internal naming. WireContractTest pins the literals.
    public static final String HEADER_MESSAGE_CORRELATION_ID = "RutebankenCorrelationId";
    public static final String HEADER_MESSAGE_FILE_HANDLE = "RutebankenFileHandle";
    public static final String HEADER_MESSAGE_PROVIDER_ID = "RutebankenProviderId";
    public static final String HEADER_MESSAGE_FILE_NAME = "RutebankenFileName";
    public static final String HEADER_MESSAGE_USERNAME = "RutebankenUsername";

    /**
     * Camel's file name header, which the Camel-based version of this service leaked onto every PubSub
     * message because it copied all exchange headers into the attributes. Marduk overwrites it from
     * {@link #HEADER_MESSAGE_FILE_NAME} on arrival and so demonstrably does not need it, but it is kept
     * on the wire because the release that removes the framework should not also change the contract.
     * Drop it in a later release.
     */
    public static final String HEADER_LEGACY_CAMEL_FILE_NAME = "CamelFileName";

    /** Identifies extime as the producer to marduk. */
    public static final String EXTIME_USERNAME = "Extime";
}
