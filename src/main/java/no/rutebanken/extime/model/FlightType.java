package no.rutebanken.extime.model;

public enum FlightType {

    DOMESTIC("D"), INTERNATIONAL("I"), SCHENGEN("I");

    private final String code;

    FlightType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
