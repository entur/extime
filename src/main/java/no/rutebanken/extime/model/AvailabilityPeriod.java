package no.rutebanken.extime.model;

import java.time.LocalDateTime;

public class AvailabilityPeriod {
    private LocalDateTime periodFromDateTime;
    private LocalDateTime periodToDateTime;

    public AvailabilityPeriod(LocalDateTime periodFromDateTime, LocalDateTime periodToDateTime) {
        if (periodFromDateTime.isAfter(periodToDateTime)) {
            throw new IllegalArgumentException("From date cannot be after to date");
        }
        this.periodFromDateTime = periodFromDateTime;
        this.periodToDateTime = periodToDateTime;
    }

    public LocalDateTime getPeriodFromDateTime() {
        return periodFromDateTime;
    }

    public void setPeriodFromDateTime(LocalDateTime periodFromDateTime) {
        this.periodFromDateTime = periodFromDateTime;
    }

    public LocalDateTime getPeriodToDateTime() {
        return periodToDateTime;
    }

    public void setPeriodToDateTime(LocalDateTime periodToDateTime) {
        this.periodToDateTime = periodToDateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AvailabilityPeriod that = (AvailabilityPeriod) o;

        if (!periodFromDateTime.equals(that.periodFromDateTime)) return false;
        return periodToDateTime.equals(that.periodToDateTime);

    }

    @Override
    public int hashCode() {
        int result = periodFromDateTime.hashCode();
        result = 31 * result + periodToDateTime.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "AvailabilityPeriod{" + "periodFromDateTime=" + periodFromDateTime +
                ", periodToDateTime=" + periodToDateTime +
                '}';
    }
}
