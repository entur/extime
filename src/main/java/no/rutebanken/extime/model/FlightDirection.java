package no.rutebanken.extime.model;

public enum FlightDirection {
    DEPARTURE("D"), ARRIVAL("A");

    private String code;

    FlightDirection(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}