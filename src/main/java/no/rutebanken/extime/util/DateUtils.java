package no.rutebanken.extime.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import no.rutebanken.extime.model.AvailabilityPeriod;
import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.List;

import static no.rutebanken.extime.Constants.DEFAULT_ZONE_ID;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_TIMETABLE_LARGE_AIRPORT_RANGE;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_TIMETABLE_SMALL_AIRPORT_RANGE;

@Component(value = "dateUtils")
public class DateUtils {

	private static final DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd")
			.optionalStart().appendPattern("XXXXX").optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .parseDefaulting(ChronoField.OFFSET_SECONDS,OffsetDateTime.now().getLong(ChronoField.OFFSET_SECONDS) )
            .toFormatter();

	public static ZonedDateTime parseDate(String dateWithZone) {
		return ZonedDateTime.parse(dateWithZone, formatter);
	}
	
    @Value("${avinor.timetable.period.months}") int numberOfMonthsInPeriod;
    @Value("${avinor.timetable.max.range}") int maxRangeDays;
    @Value("${avinor.timetable.min.range}") int minRangeDays;
    @Value("${netex.export.time.zone.id:CET}")
    private ZoneId exportZoneId;

    public LocalDateTime toExportLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, exportZoneId);
    }

    public LocalDateTime toExportLocalDateTime(ZonedDateTime zonedDateTime){
        return zonedDateTime.withZoneSameInstant(exportZoneId).toLocalDateTime();
    }

    public LocalTime toExportLocalTime(ZonedDateTime zonedDateTime){
        return zonedDateTime.withZoneSameInstant(exportZoneId).toLocalTime();
    }

    public AvailabilityPeriod generateAvailabilityPeriod() {
        LocalDate requestPeriodFromDate = LocalDate.now(ZoneId.of(DEFAULT_ZONE_ID));
        LocalDate requestPeriodToDate = requestPeriodFromDate.plusMonths(numberOfMonthsInPeriod);
        return new AvailabilityPeriod(requestPeriodFromDate.atStartOfDay(), requestPeriodToDate.atStartOfDay());
    }

    public void generateDateRanges(Exchange exchange) {
        exchange.getIn().setHeader(HEADER_TIMETABLE_SMALL_AIRPORT_RANGE, generateDateRanges(maxRangeDays));
        exchange.getIn().setHeader(HEADER_TIMETABLE_LARGE_AIRPORT_RANGE, generateDateRanges(minRangeDays));
    }

    public List<Range<LocalDate>> generateDateRanges(long numberOfDaysInRange) {
        LocalDate rangeStartDate = LocalDate.now(ZoneId.of(DEFAULT_ZONE_ID));
        List<Range<LocalDate>> dateRanges = Lists.newArrayList();
        LocalDate periodEndDate = rangeStartDate.plusMonths(numberOfMonthsInPeriod);

        while (!rangeStartDate.isAfter(periodEndDate)) {
            LocalDate rangeEndDate = rangeStartDate.plusDays(numberOfDaysInRange).isBefore(periodEndDate)
                    ? rangeStartDate.plusDays(numberOfDaysInRange) : periodEndDate;
            dateRanges.add(Range.closed(rangeStartDate, rangeEndDate));
            rangeStartDate = rangeStartDate.plusDays(numberOfDaysInRange + 1);
        }

        return dateRanges;
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
