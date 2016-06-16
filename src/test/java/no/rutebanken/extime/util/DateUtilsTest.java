package no.rutebanken.extime.util;

import com.google.common.collect.Range;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.*;

public class DateUtilsTest {

    private DateUtils clazzUnderTest;
    private Exchange exchange;

    @Before
    public void setUp() throws Exception {
        clazzUnderTest = new DateUtils();
        exchange = new DefaultExchange(new DefaultCamelContext());
        clazzUnderTest.numberOfMonthsInPeriod = 3;
        clazzUnderTest.maxRangeDays = 180;
        clazzUnderTest.medRangeDays = 60;
        clazzUnderTest.minRangeDays = 7;
    }

    @Test
    public void testGenerateDateRangesInHeaders() {
        clazzUnderTest.generateDateRanges(exchange);

        List smallAirportRanges = exchange.getIn().getHeader(HEADER_TIMETABLE_SMALL_AIRPORT_RANGE, List.class);
        Assertions.assertThat(smallAirportRanges)
                .isNotNull()
                .isNotEmpty()
                .hasSize(1);

        List mediumAirportRanges = exchange.getIn().getHeader(HEADER_TIMETABLE_MEDIUM_AIRPORT_RANGE, List.class);
        Assertions.assertThat(mediumAirportRanges)
                .isNotNull()
                .isNotEmpty()
                .hasSize(2);

        List largeAirportRanges = exchange.getIn().getHeader(HEADER_TIMETABLE_LARGE_AIRPORT_RANGE, List.class);
        Assertions.assertThat(largeAirportRanges)
                .isNotNull()
                .isNotEmpty()
                .hasSize(12);
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

}