package no.rutebanken.extime.model;

public enum StopVisitType {
    DEPARTURE("D"), ARRIVAL("A");

    private String code;

    StopVisitType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}