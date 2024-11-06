package no.rutebanken.extime.model;

import java.time.LocalDateTime;

public record AvailabilityPeriod(LocalDateTime periodFromDateTime, LocalDateTime periodToDateTime) {

    public AvailabilityPeriod {
        if (periodFromDateTime.isAfter(periodToDateTime)) {
            throw new IllegalArgumentException("From date cannot be after to date");
        }
    }
}
