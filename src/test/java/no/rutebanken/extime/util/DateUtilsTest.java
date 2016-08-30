package no.rutebanken.extime.util;

import com.google.common.collect.Range;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_TIMETABLE_LARGE_AIRPORT_RANGE;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_TIMETABLE_SMALL_AIRPORT_RANGE;

public class DateUtilsTest {

    private DateUtils clazzUnderTest;
    private Exchange exchange;

    @Before
    public void setUp() throws Exception {
        clazzUnderTest = new DateUtils();
        exchange = new DefaultExchange(new DefaultCamelContext());
        clazzUnderTest.numberOfMonthsInPeriod = 3;
        clazzUnderTest.maxRangeDays = 180;
        clazzUnderTest.minRangeDays = 60;
    }

    @Test
    public void testDateParsing() {
        LocalDate localDate = LocalDate.parse("2016-12-24");
        System.out.println(localDate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGenerateDateRangesInHeaders() {
        clazzUnderTest.generateDateRanges(exchange);

        List smallAirportRanges = exchange.getIn().getHeader(HEADER_TIMETABLE_SMALL_AIRPORT_RANGE, List.class);
        Assertions.assertThat(smallAirportRanges)
                .isNotNull()
                .isNotEmpty()
                .hasSize(1);

        List largeAirportRanges = exchange.getIn().getHeader(HEADER_TIMETABLE_LARGE_AIRPORT_RANGE, List.class);
        Assertions.assertThat(largeAirportRanges)
                .isNotNull()
                .isNotEmpty()
                .hasSize(2);
    }

    @Test
    public void testGenerateDateRangeListForSmallAirport() {
        List<Range<LocalDate>> dateRanges = clazzUnderTest.generateDateRanges(180);
        Assertions.assertThat(dateRanges)
                .isNotNull()
                .isNotEmpty()
                .hasSize(1);
        Assertions.assertThat(dateRanges.get(0).lowerEndpoint())
                .isEqualTo(LocalDate.now());
        Assertions.assertThat(dateRanges.get(0).upperEndpoint())
                .isEqualTo(LocalDate.now().plusMonths(3));
    }

    @Test
    public void testGenerateDateRangeListForMediumAirport() {
        List<Range<LocalDate>> dateRanges = clazzUnderTest.generateDateRanges(60);
        Assertions.assertThat(dateRanges)
                .isNotNull()
                .isNotEmpty()
                .hasSize(2);
    }

    @Test
    public void testGenerateDateRangeListForLargeAirport() {
        List<Range<LocalDate>> dateRanges = clazzUnderTest.generateDateRanges(7);
        Assertions.assertThat(dateRanges)
                .isNotNull()
                .isNotEmpty()
                .hasSize(12);
    }

    @Test
    public void testFormatLocalDate() {
        String localDateFormat = clazzUnderTest.format(LocalDate.parse("2017-01-01"));
        Assertions.assertThat(localDateFormat)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("2017-01-01");
    }

    @Test
    public void testIsWorkDays() {
        List<DayOfWeek> workDays = Arrays.asList(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        );
        List<Boolean> results = new ArrayList<>();

        workDays.forEach(workDay -> {
            Boolean isWorkDay = workDay.query(DateUtils.WorkDays::isWorkDay);
            results.add(isWorkDay);
        });

        Assertions.assertThat(results)
                .isNotNull()
                .isNotEmpty()
                .hasSameSizeAs(workDays)
                .allMatch((Predicate<Boolean>) isWorkDay -> isWorkDay.equals(Boolean.TRUE));
    }

    @Test
    public void testIsNotWorkDays() {
        List<DayOfWeek> weekendDays = Arrays.asList(
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY
        );
        List<Boolean> results = new ArrayList<>();

        weekendDays.forEach(workDay -> {
            Boolean isWorkDay = workDay.query(DateUtils.WorkDays::isWorkDay);
            results.add(isWorkDay);
        });

        Assertions.assertThat(results)
                .isNotNull()
                .isNotEmpty()
                .hasSameSizeAs(weekendDays)
                .allMatch((Predicate<Boolean>) isWorkDay -> isWorkDay.equals(Boolean.FALSE));
    }

    @Test
    public void testIsNotWeekendDays() {
        List<DayOfWeek> workDays = Arrays.asList(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        );
        List<Boolean> results = new ArrayList<>();

        workDays.forEach(workDay -> {
            Boolean isWeekendDay = workDay.query(DateUtils.WeekendDays::isWeekendkDay);
            results.add(isWeekendDay);
        });

        Assertions.assertThat(results)
                .isNotNull()
                .isNotEmpty()
                .hasSameSizeAs(workDays)
                .allMatch((Predicate<Boolean>) isWeekendDay -> isWeekendDay.equals(Boolean.FALSE));
    }

    @Test
    public void testIsWeekendDays() {
        List<DayOfWeek> weekendDays = Arrays.asList(
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY
        );
        List<Boolean> results = new ArrayList<>();

        weekendDays.forEach(weekendDay -> {
            Boolean isWeekendDay = weekendDay.query(DateUtils.WeekendDays::isWeekendkDay);
            results.add(isWeekendDay);
        });

        Assertions.assertThat(results)
                .isNotNull()
                .isNotEmpty()
                .hasSameSizeAs(weekendDays)
                .allMatch((Predicate<Boolean>) isWeekendDay -> isWeekendDay.equals(Boolean.TRUE));
    }

}