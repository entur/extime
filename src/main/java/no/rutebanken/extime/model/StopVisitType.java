package no.rutebanken.extime.model;

public enum StopVisitType {
    ARRIVAL("A"), DEPARTURE("D");

    private String code;

    StopVisitType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}