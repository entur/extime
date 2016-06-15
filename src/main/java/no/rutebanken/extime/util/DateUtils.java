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

    @Value("${avinor.timetable.period.months}") int numberOfMonthsInPeriod;
    @Value("${avinor.timetable.max.range}") int maxRangeDays;
    @Value("${avinor.timetable.med.range}") int medRangeDays;
    @Value("${avinor.timetable.min.range}") int minRangeDays;

    public void generateDateRanges(Exchange exchange) {
        exchange.getIn().setHeader(HEADER_TIMETABLE_SMALL_AIRPORT_RANGE, generateDateRanges(maxRangeDays));
        exchange.getIn().setHeader(HEADER_TIMETABLE_MEDIUM_AIRPORT_RANGE, generateDateRanges(medRangeDays));
        exchange.getIn().setHeader(HEADER_TIMETABLE_LARGE_AIRPORT_RANGE, generateDateRanges(minRangeDays));
    }

    public List<Range<LocalDate>> generateDateRanges(int numberOfDaysInRange) {
        LocalDate rangeStartDate = LocalDate.now();
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

}
