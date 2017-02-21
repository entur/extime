package no.rutebanken.extime.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import no.rutebanken.extime.model.AvailabilityPeriod;
import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.List;

import static no.rutebanken.extime.Constants.DEFAULT_ZONE_ID;
import static no.rutebanken.extime.Constants.OFFSET_MIDNIGHT_UTC;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_TIMETABLE_LARGE_AIRPORT_RANGE;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_TIMETABLE_SMALL_AIRPORT_RANGE;

@Component(value = "dateUtils")
public class DateUtils {

    @Value("${avinor.timetable.period.months}") int numberOfMonthsInPeriod;
    @Value("${avinor.timetable.max.range}") int maxRangeDays;
    @Value("${avinor.timetable.min.range}") int minRangeDays;

    public AvailabilityPeriod generateAvailabilityPeriod() {
        LocalDate requestPeriodFromDate = LocalDate.now(ZoneId.of(DEFAULT_ZONE_ID));
        LocalDate requestPeriodToDate = requestPeriodFromDate.plusMonths(numberOfMonthsInPeriod);
        OffsetTime offsetMidnight = OffsetTime.parse(OFFSET_MIDNIGHT_UTC).withOffsetSameLocal(ZoneOffset.UTC);
        OffsetDateTime requestPeriodFromDateTime = requestPeriodFromDate.atTime(offsetMidnight);
        OffsetDateTime requestPeriodToDateTime = requestPeriodToDate.atTime(offsetMidnight);
        return new AvailabilityPeriod(requestPeriodFromDateTime, requestPeriodToDateTime);
    }

    public void generateDateRanges(Exchange exchange) {
        exchange.getIn().setHeader(HEADER_TIMETABLE_SMALL_AIRPORT_RANGE, generateDateRanges(maxRangeDays));
        exchange.getIn().setHeader(HEADER_TIMETABLE_LARGE_AIRPORT_RANGE, generateDateRanges(minRangeDays));
    }

    public List<Range<LocalDate>> generateDateRanges(int numberOfDaysInRange) {
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
        public static boolean isWorkDay(TemporalAccessor date) {
            int day = date.get(ChronoField.DAY_OF_WEEK);
            return day >= 1 && day <= 5;
        }
    }

    public static class WeekendDays {
        public static boolean isWeekendkDay(TemporalAccessor date) {
            int day = date.get(ChronoField.DAY_OF_WEEK);
            return day >= 6 && day <= 7;
        }
    }
}
