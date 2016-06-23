package no.rutebanken.extime.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.model.AirlineIATA;
import no.rutebanken.extime.model.AirportIATA;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.*;

@Component(value = "dateUtils")
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
        LocalDate rangeStartDate = LocalDate.now(ZoneId.of("Europe/Oslo"));
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

    public void findUniqueAirlines(List<Flight> flights) {
        System.out.printf("%nUnique airline designators:%n");
        flights.stream()
                .map(Flight::getAirlineDesignator)
                .distinct()
                .sorted()
                .forEach(System.out::println);
    }
}
