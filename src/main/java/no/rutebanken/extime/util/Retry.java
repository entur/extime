package no.rutebanken.extime.util;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Replaces Camel's route-level {@code errorHandler}, which retried every step of every route with
 * exponential backoff.
 *
 * <p>The deployed configuration never set {@code extime.camel.redelivery.*}, so production ran on the
 * code defaults: three redeliveries, 5s initial delay, multiplier 3, i.e. retries after 5s, 15s and 45s.
 * Those are the defaults of {@link RetrySettings} so that the behaviour carries over unchanged.
 *
 * <p>What does not carry over is the breadth. Camel wrapped everything, including the CPU-bound NeTEx
 * conversion, where a retry only repeats a deterministic failure. This is applied to the I/O that can
 * fail transiently: the Avinor feed, the blob store and the PubSub publish.
 */
public final class Retry {

    private static final Logger LOGGER = LoggerFactory.getLogger(Retry.class);

    private Retry() {
    }

    public static <T> T withRetry(RetrySettings settings, String description, Callable<T> work) {
        int attempt = 0;
        Duration delay = settings.delay();
        while (true) {
            try {
                return work.call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExtimeException(description + " was interrupted", e);
            } catch (Exception e) {
                attempt++;
                if (attempt > settings.maxRetries()) {
                    throw new ExtimeException(
                            "%s failed after %d attempt(s)".formatted(description, attempt), e);
                }
                logRetry(description, e, attempt, settings.maxRetries(), delay);
                sleep(delay, description);
                delay = delay.multipliedBy(settings.backOffMultiplier());
            }
        }
    }

    public static void withRetry(RetrySettings settings, String description, ThrowingRunnable work) {
        withRetry(settings, description, () -> {
            work.run();
            return null;
        });
    }

    private static void logRetry(String description, Exception e, int attempt, int maxRetries, Duration delay) {
        Throwable rootCause = ExceptionUtils.getRootCause(e);
        Throwable reported = rootCause != null ? rootCause : e;
        LOGGER.warn("{} failed ({}: {}). Retrying in {}, attempt {}/{}...",
                description, reported.getClass().getName(), reported.getMessage(), delay, attempt, maxRetries);
    }

    private static void sleep(Duration delay, String description) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExtimeException(description + " was interrupted while waiting to retry", e);
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
