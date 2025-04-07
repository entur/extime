package no.rutebanken.extime.util;

import no.rutebanken.extime.model.AvailabilityPeriod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

import static no.rutebanken.extime.Constants.DEFAULT_ZONE_ID;

@Component(value = "dateUtils")
public class DateUtils {

    private final Duration durationForward;
    private final ZoneId exportZoneId;


    public DateUtils(@Value("${avinor.timetable.period.forward:14d}") Duration durationForward, @Value("${netex.export.time.zone.id:CET}") ZoneId exportZoneId) {
        this.durationForward = durationForward;
        this.exportZoneId = exportZoneId;
    }

    public LocalDateTime toExportLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, exportZoneId);
    }



    public LocalDate toExportLocalDate(ZonedDateTime zonedDateTime){
        return zonedDateTime.withZoneSameInstant(exportZoneId).toLocalDate();
    }

    public LocalTime toExportLocalTime(ZonedDateTime zonedDateTime){
        return zonedDateTime.withZoneSameInstant(exportZoneId).toLocalTime();
    }

    public AvailabilityPeriod generateAvailabilityPeriod() {
        LocalDate requestPeriodFromDate = LocalDate.now(ZoneId.of(DEFAULT_ZONE_ID));
        LocalDate requestPeriodToDate = requestPeriodFromDate.plusDays(durationForward.toDays());
        return new AvailabilityPeriod(requestPeriodFromDate.atStartOfDay(), requestPeriodToDate.atStartOfDay());
    }



    public String format(LocalDate localDate) {
        return localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    public String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    public static class WorkDays {
        private WorkDays() {
            throw new IllegalStateException("Utility class - should not be instantiated");
        }
        public static boolean isWorkDay(TemporalAccessor date) {
            int day = date.get(ChronoField.DAY_OF_WEEK);
            return day >= 1 && day <= 5;
        }
    }

    public static class WeekendDays {
        private WeekendDays() {
            throw new IllegalStateException("Utility class - should not be instantiated");
        }
        public static boolean isWeekendkDay(TemporalAccessor date) {
            int day = date.get(ChronoField.DAY_OF_WEEK);
            return day >= 6 && day <= 7;
        }
    }
}
