package no.rutebanken.extime.model;

import java.time.OffsetDateTime;

public class AvailabilityPeriod {
    private OffsetDateTime periodFromDateTime;
    private OffsetDateTime periodToDateTime;

    public AvailabilityPeriod(OffsetDateTime periodFromDateTime, OffsetDateTime periodToDateTime) throws IllegalArgumentException {
        if (periodFromDateTime.isAfter(periodToDateTime)) {
            throw new IllegalArgumentException("From date cannot be after to date");
        }
        this.periodFromDateTime = periodFromDateTime;
        this.periodToDateTime = periodToDateTime;
    }

    public OffsetDateTime getPeriodFromDateTime() {
        return periodFromDateTime;
    }

    public void setPeriodFromDateTime(OffsetDateTime periodFromDateTime) {
        this.periodFromDateTime = periodFromDateTime;
    }

    public OffsetDateTime getPeriodToDateTime() {
        return periodToDateTime;
    }

    public void setPeriodToDateTime(OffsetDateTime periodToDateTime) {
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
        final StringBuilder sb = new StringBuilder("AvailabilityPeriod{");
        sb.append("periodFromDateTime=").append(periodFromDateTime);
        sb.append(", periodToDateTime=").append(periodToDateTime);
        sb.append('}');
        return sb.toString();
    }
}
