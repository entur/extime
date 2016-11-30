package no.rutebanken.extime.model;

public enum ServiceType {

    SCHEDULED_PASSENGER_NORMAL("J"),
    SCHEDULED_PASSENGER_SHUTTLE("S"),
    SCHEDULED_PASSENGER_SERVICE("U"),
    SCHEDULED_PASSENGER_CARGO("Q");

    private String code;

    ServiceType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ServiceType fromCode(String code) {
        for (ServiceType serviceType : values()) {
            if (serviceType.code.equals(code)) {
                return serviceType;
            }
        }
        return null;
    }
}
