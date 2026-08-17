package no.rutebanken.extime.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the attempt count and the backoff, which are the numbers Camel's {@code defaultErrorHandler}
 * ran with in production: three redeliveries after the first try, 5s apart, multiplied by three.
 */
class RetryTest {

    private static final RetrySettings FAST = new RetrySettings(3, Duration.ofMillis(1), 2);
    private static final RetrySettings NO_RETRIES = new RetrySettings(0, Duration.ofMillis(1), 3);

    @Test
    void returnsTheFirstSuccessWithoutRetrying() {
        AtomicInteger attempts = new AtomicInteger();

        String result = Retry.withRetry(FAST, "work", () -> {
            attempts.incrementAndGet();
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void retriesUntilTheWorkSucceeds() {
        AtomicInteger attempts = new AtomicInteger();

        String result = Retry.withRetry(FAST, "work", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IOException("not yet");
            }
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void makesOneAttemptMoreThanTheConfiguredRetries() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> Retry.withRetry(FAST, "work", () -> {
            attempts.incrementAndGet();
            throw new IOException("always fails");
        }))
                .isInstanceOf(ExtimeException.class)
                .hasMessageContaining("work failed after 4 attempt(s)")
                .hasRootCauseMessage("always fails");

        assertThat(attempts).hasValue(1 + FAST.maxRetries());
    }

    @Test
    void doesNotRetryWhenRetriesAreDisabled() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> Retry.withRetry(NO_RETRIES, "work", () -> {
            attempts.incrementAndGet();
            throw new IOException("fails");
        })).isInstanceOf(ExtimeException.class);

        assertThat(attempts).hasValue(1);
    }

    /**
     * Timed rather than inspected, because reading the delay sequence back out of {@link RetrySettings}
     * would assert the test's own arithmetic. A linear retry sleeps 3 x 50ms here; an exponential one
     * sleeps 50 + 100 + 200.
     */
    @Test
    void backsOffExponentially() {
        RetrySettings settings = new RetrySettings(3, Duration.ofMillis(50), 2);

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> Retry.withRetry(settings, "work", () -> {
            throw new IOException("always fails");
        })).isInstanceOf(ExtimeException.class);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(elapsed).isGreaterThan(Duration.ofMillis(300));
    }

    @Test
    void theVoidOverloadRetriesToo() {
        AtomicInteger attempts = new AtomicInteger();

        Retry.withRetry(FAST, "work", () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new IOException("not yet");
            }
        });

        assertThat(attempts).hasValue(2);
    }
}
