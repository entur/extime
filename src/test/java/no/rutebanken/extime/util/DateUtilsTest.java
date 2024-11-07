package no.rutebanken.extime.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


class DateUtilsTest {

    private DateUtils clazzUnderTest;

    @BeforeEach
    void setUp() {
        clazzUnderTest = new DateUtils(Duration.ofDays(4), ZoneId.of("CET"));
    }

    @Test
    void testFormatLocalDate() {
        String localDateFormat = clazzUnderTest.format(LocalDate.parse("2017-01-01"));
        Assertions.assertThat(localDateFormat)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("2017-01-01");
    }

    @Test
    void testIsWorkDays() {
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
                .allMatch(isWorkDay -> isWorkDay.equals(Boolean.TRUE));
    }

    @Test
    void testIsNotWorkDays() {
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
                .allMatch(isWorkDay -> isWorkDay.equals(Boolean.FALSE));
    }

    @Test
    void testIsNotWeekendDays() {
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
                .allMatch(isWeekendDay -> isWeekendDay.equals(Boolean.FALSE));
    }

    @Test
    void testIsWeekendDays() {
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
                .allMatch(isWeekendDay -> isWeekendDay.equals(Boolean.TRUE));
    }

}