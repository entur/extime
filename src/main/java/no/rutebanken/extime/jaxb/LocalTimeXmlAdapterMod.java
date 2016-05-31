package no.rutebanken.extime.jaxb;

import com.migesok.jaxb.adapter.javatime.TemporalAccessorXmlAdapter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class LocalTimeXmlAdapterMod extends TemporalAccessorXmlAdapter<LocalTime> {
    public LocalTimeXmlAdapterMod() {
        super(DateTimeFormatter.ISO_TIME, LocalTime::from);
    }
}