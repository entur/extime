package no.rutebanken.extime;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;

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
    // The English locale is set explicitly so that day and month patterns (MON, TUE,...) are output in English even if the runtime environment has a different locale.
    public static final Locale DEFAULT_DATE_TIME_FORMATTER_LOCALE = Locale.ENGLISH;
    public static final DateTimeFormatter DEFAULT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withLocale(DEFAULT_DATE_TIME_FORMATTER_LOCALE);
    public static final DateTimeFormatter DAY_TYPE_FORMATTER = DateTimeFormatter.ofPattern("MMM_EEE_dd").withLocale(DEFAULT_DATE_TIME_FORMATTER_LOCALE);
    public static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withLocale(DEFAULT_DATE_TIME_FORMATTER_LOCALE);
    public static final DateTimeFormatter ZONED_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder().append(DEFAULT_DATE_TIME_FORMATTER)
            .optionalStart().appendPattern("XXXXX").optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .parseDefaulting(ChronoField.OFFSET_SECONDS, OffsetDateTime.now().getLong(ChronoField.OFFSET_SECONDS) )
            .toFormatter().withLocale(DEFAULT_DATE_TIME_FORMATTER_LOCALE);

    // netex specific constants
    public static final String NETEX_PROFILE_VERSION = "1.08:NO-NeTEx-networktimetable:1.1";
    public static final String VERSION_ONE = "1";

    public static final String NSR_XMLNS = "NSR";
    public static final String NSR_XMLNSURL = "http://www.rutebanken.org/ns/nsr";
    
    public static final String AVINOR_XMLNS = "AVI";
    public static final String AVINOR_XMLNSURL = "http://www.rutebanken.org/ns/avi";
    
    public static final String DEFAULT_COORDINATE_SYSTEM = "WGS84";
    public static final String DEFAULT_ID_SEPARATOR = ":";

}
