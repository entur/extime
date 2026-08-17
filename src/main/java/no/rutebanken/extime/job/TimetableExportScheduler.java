package no.rutebanken.extime.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the daily export.
 *
 * <p>Replaces the {@code quartz://avinorTimetableScheduler?cron=...} consumer. The cron expression is
 * the same six-field Quartz form, which Spring's {@code CronExpression} also understands, including the
 * {@code ?} placeholder. The property is new -- {@code extime.timetable.scheduler.cron} rather than
 * {@code avinor.timetable.scheduler.consumer} -- because the old value was a URI and had its spaces
 * escaped as {@code +}. Set it to {@code -} to disable the schedule.
 *
 * <p>Nothing guards against two pods firing at once, and nothing did before: extime runs at one replica
 * and the Camel version had no {@code master:} route either. A rolling deploy that overlaps the cron
 * minute can still produce two exports, each of which is a complete dataset, so marduk imports one and
 * then the other.
 */
@Component
public class TimetableExportScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimetableExportScheduler.class);

    private final TimetableExportJob timetableExportJob;

    public TimetableExportScheduler(TimetableExportJob timetableExportJob) {
        this.timetableExportJob = timetableExportJob;
    }

    // No default: a missing property must fail the context rather than disable the export in silence.
    // Set the value to "-" to disable it deliberately, as the tests do.
    @Scheduled(cron = "${extime.timetable.scheduler.cron}")
    void exportTimetable() {
        try {
            timetableExportJob.export();
        } catch (RuntimeException e) {
            // Left to Spring, the failure is logged by TaskUtils under its own name, which is not where
            // anyone looks for extime's errors. Reported here instead; the next run is unaffected either
            // way, since a cron task is rescheduled after a suppressed error.
            LOGGER.error("Scheduled Avinor timetable export failed", e);
        }
    }
}
