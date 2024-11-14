package no.rutebanken.extime.model;

import java.time.LocalDate;

public record FlightRequest(String uri, String airportName, LocalDate fromDate, LocalDate toDate) {

    public String request() {
        return "%s?airport=%s&PeriodFrom=%s&PeriodTo=%s&direction=D".formatted(uri, airportName, fromDate, toDate);
    }

}

