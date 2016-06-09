package no.rutebanken.extime.util;

import com.google.common.collect.Range;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

public class DateUtilsTest {

    private DateUtils clazzUnderTest;
    private Exchange mockExchange;

    @Before
    public void setUp() throws Exception {
        clazzUnderTest = new DateUtils();
        mockExchange = Mockito.mock(Exchange.class);
        Message mockCamelMessage = Mockito.mock(Message.class);
        Mockito.when(mockExchange.getIn()).thenReturn(mockCamelMessage);
    }

    @Test
    public void testGenerateDateRangeListForSmallAirport() {
        List<Range<LocalDate>> dateRanges = clazzUnderTest.generateDateRanges(3, 180, LocalDate.now());
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
        List<Range<LocalDate>> dateRanges = clazzUnderTest.generateDateRanges(3, 60, LocalDate.now());
        Assertions.assertThat(dateRanges)
                .isNotNull()
                .isNotEmpty()
                .hasSize(2);
    }

    @Test
    public void testGenerateDateRangeListForLargeAirport() {
        List<Range<LocalDate>> dateRanges = clazzUnderTest.generateDateRanges(3, 7, LocalDate.now());
        Assertions.assertThat(dateRanges)
                .isNotNull()
                .isNotEmpty()
                .hasSize(12);
    }

}