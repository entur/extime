package no.rutebanken.extime.xmladapter;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

public class ZonedDateTimeFromDateXmlAdapter extends XmlAdapter<String, ZonedDateTime> {
    private static final DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd")
            .optionalStart().appendPattern("XXXXX").optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .parseDefaulting(ChronoField.OFFSET_SECONDS, OffsetDateTime.now().getLong(ChronoField.OFFSET_SECONDS))
            .toFormatter();

    @Override
    public ZonedDateTime unmarshal(String inputDate) {
        return ZonedDateTime.parse(inputDate, formatter);
    }

    @Override
    public String marshal(ZonedDateTime inputDate) {
        if (inputDate != null) {
            return formatter.format(inputDate);
        } else {
            return null;
        }
    }
}
