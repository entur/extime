package no.rutebanken.extime.xmladapter;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.time.ZonedDateTime;

import static no.rutebanken.extime.Constants.ZONED_DATE_TIME_FORMATTER;

public class ZonedDateTimeFromDateXmlAdapter extends XmlAdapter<String, ZonedDateTime> {

    @Override
    public ZonedDateTime unmarshal(String inputDate) throws Exception {
        return ZonedDateTime.parse(inputDate, ZONED_DATE_TIME_FORMATTER);
    }

    @Override
    public String marshal(ZonedDateTime inputDate) throws Exception {
        if (inputDate != null) {
            return ZONED_DATE_TIME_FORMATTER.format(inputDate);
        } else {
            return null;
        }
    }
}
