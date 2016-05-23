package no.rutebanken.extime.model;

public enum AirlineIATA {
    DY("Norwegian"), SK("SAS"), WF("Widerøe");

    private String airportName;

    AirlineIATA(String airportName) {
        this.airportName = airportName;
    }

    public String getAirportName() {
        return airportName;
    }
}
