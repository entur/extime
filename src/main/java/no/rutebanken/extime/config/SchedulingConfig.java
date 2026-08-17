package no.rutebanken.extime.config;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * The scheduler behind {@code @Scheduled}.
 *
 * <p>Spring Boot would normally contribute this, but {@code TaskSchedulingAutoConfiguration} backs off
 * when any other {@code TaskScheduler} bean exists, and spring-cloud-gcp registers several of its own.
 * Without a bean named {@code taskScheduler}, {@code @Scheduled} builds a single-threaded executor
 * instead and silently ignores {@code spring.task.scheduling.*}. It says so once per boot at INFO, from
 * {@code TaskSchedulerRouter}, which is easy to miss.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }
}
