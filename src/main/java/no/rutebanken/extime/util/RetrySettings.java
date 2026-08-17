package no.rutebanken.extime.util;

import java.time.Duration;

/**
 * Retry parameters for the I/O the pipeline depends on. The defaults reproduce what Camel's
 * {@code defaultErrorHandler} did in production: three redeliveries, 5s apart, multiplied by 3 each
 * time.
 */
public record RetrySettings(int maxRetries, Duration delay, int backOffMultiplier) {
}
