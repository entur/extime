package no.rutebanken.extime.job;

import no.rutebanken.extime.util.ExtimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The deployed cron expressions were written for Camel's quartz component and are now parsed by Spring.
 * Both accept the six-field Quartz form, including the {@code ?} placeholder, but only one of them fails
 * loudly on an expression it cannot read: an unparseable value fails the context at startup, whereas a
 * parseable one that never fires is silent. These are the values in
 * {@code helm/rorextime/env/values-kub-ent-*.yaml}, with the URI-escaped spaces restored.
 */
class TimetableExportSchedulerTest {

    @ParameterizedTest
    @ValueSource(strings = { "0 4 3 * * ?", "0 0 5 * * ?", "0 58 3 * * ?" })
    void deployedCronExpressionsFireDaily(String cron) {
        CronExpression expression = CronExpression.parse(cron);

        LocalDateTime firstRun = expression.next(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(firstRun).isNotNull();
        assertThat(expression.next(firstRun)).isEqualTo(firstRun.plusDays(1));
    }

    /**
     * A failed export must not escape the scheduler, or tomorrow's run depends on how the surrounding
     * executor happens to treat an exception.
     */
    @Test
    void aFailedExportDoesNotPropagate() {
        TimetableExportJob failing = mock(TimetableExportJob.class);
        doThrow(new ExtimeException("Avinor is down")).when(failing).export();

        assertThatCode(() -> new TimetableExportScheduler(failing).exportTimetable()).doesNotThrowAnyException();

        verify(failing).export();
    }
}
