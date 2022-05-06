package no.rutebanken.extime.model;

public enum AirlineIATA {
    DY("Norwegian"), FI("Icelandair"), M3("Air Norway"), SK("SAS"), WF("Widerøe"),
    AY("Finnair"), BT("Air Baltic"), D8("Norwegian"), DX("DAT Danish Air Transport"),
    LTR("Lufttransport"), RC("Atlantic Airways"), FR("Ryanair"), EJU("EasyJet"),
    KL("KLM"), W2("Flexflight"), AF("Air France"), BA("British Airways"),
    LH("Lufthansa"), LO("LOT Polish Airlines"), SN("Brussels International Airlines"),
    SU("Aeroflot Russian Airlines"), W6("Wizz Air"),
    FL("Air Leap"), FS("Flyr");

    private final String airlineName;

    AirlineIATA(String airlineName) {
        this.airlineName = airlineName;
    }

    }
