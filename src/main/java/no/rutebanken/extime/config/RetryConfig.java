package no.rutebanken.extime.config;

import no.rutebanken.extime.util.RetrySettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RetryConfig {

    @Bean
    RetrySettings retrySettings(
            @Value("${extime.retry.max:3}") int maxRetries,
            @Value("${extime.retry.delay:5000ms}") Duration delay,
            @Value("${extime.retry.backoff.multiplier:3}") int backOffMultiplier) {
        return new RetrySettings(maxRetries, delay, backOffMultiplier);
    }
}
