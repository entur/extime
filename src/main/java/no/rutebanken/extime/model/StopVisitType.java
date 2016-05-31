package no.rutebanken.extime.model;

/**
 * @todo: change type name to StopVisitType
 */
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