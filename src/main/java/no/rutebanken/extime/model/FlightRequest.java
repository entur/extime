package no.rutebanken.extime.model;

public record FlightRequest(String uri, String airportName, long fromHour, long toHour) {

    public String request() {
        return "%s?airport=%s&TimeFrom=%s&TimeTo=%s&serviceType=J".formatted(uri, airportName, fromHour, toHour);
    }

}
