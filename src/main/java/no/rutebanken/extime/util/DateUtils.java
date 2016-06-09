package no.rutebanken.extime.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.*;

@Component
public class DateUtils {

    @Value("${avinor.timetable.period.months}") private int periodMonths;
    @Value("${avinor.timetable.max.range}") private int maxRangeDays;
    @Value("${avinor.timetable.med.range}") private int medRangeDays;
    @Value("${avinor.timetable.min.range}") private int minRangeDays;

    public void generateDateRanges(Exchange exchange) {
        LocalDate rangeStartDate = LocalDate.now();

        exchange.getIn().setHeader(HEADER_TIMETABLE_SMALL_AIRPORT_RANGE,
                generateDateRanges(getPeriodMonths(), getMaxRangeDays(), rangeStartDate));

        exchange.getIn().setHeader(HEADER_TIMETABLE_MEDIUM_AIRPORT_RANGE,
                generateDateRanges(getPeriodMonths(), getMedRangeDays(), rangeStartDate));

        exchange.getIn().setHeader(HEADER_TIMETABLE_LARGE_AIRPORT_RANGE,
                generateDateRanges(getPeriodMonths(), getMinRangeDays(), rangeStartDate));
    }

    public List<Range<LocalDate>> generateDateRanges(int numberOfMonthsInPeriod, int numberOfDaysInRange, LocalDate rangeStartDate) {
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

    public int getPeriodMonths() {
        return periodMonths;
    }

    public int getMaxRangeDays() {
        return maxRangeDays;
    }

    public int getMedRangeDays() {
        return medRangeDays;
    }

    public int getMinRangeDays() {
        return minRangeDays;
    }
}
