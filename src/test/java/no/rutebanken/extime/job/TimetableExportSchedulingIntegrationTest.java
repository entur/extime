package no.rutebanken.extime.job;

import no.rutebanken.extime.ExtimeSpringBootTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;

/**
 * Proves the schedule is wired: the property name, {@code @EnableScheduling} and the cron parser all
 * have to line up for the export to run at all, and none of them says anything when they do not.
 * Every other test disables the schedule, so this is the only one that would notice.
 *
 * <p>The thread name settles the second half of it. Spring Boot's scheduler auto-configuration backs
 * off when any other {@code TaskScheduler} bean exists -- spring-cloud-gcp registers one for the
 * publisher -- and {@code @Scheduled} then quietly builds a single-threaded executor of its own,
 * ignoring {@code spring.task.scheduling.*}. {@code SchedulingConfig} exists to prevent that, and this
 * is what shows it worked.
 */
@SpringBootTest(properties = "extime.timetable.scheduler.cron=* * * * * ?")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TimetableExportSchedulingIntegrationTest extends ExtimeSpringBootTestBase {

    @MockitoBean
    private TimetableExportJob timetableExportJob;

    @Test
    void theExportRunsOnTheConfiguredScheduler() {
        AtomicReference<String> schedulerThread = new AtomicReference<>();
        doAnswer(invocation -> {
            schedulerThread.set(Thread.currentThread().getName());
            return null;
        }).when(timetableExportJob).export();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(schedulerThread.get()).isNotNull());

        // "scheduling-" is the prefix Boot's ThreadPoolTaskSchedulerBuilder gives the taskScheduler bean.
        // The fallback @Scheduled builds for itself is a plain Executors pool, named "pool-N-thread-M".
        assertThat(schedulerThread.get()).startsWith("scheduling-");
    }
}
