package no.rutebanken.extime.model;

public enum AirlineIATA {
    DY("Norwegian"), FI("Icelandair"), M3("Air Norway"), SK("SAS"), WF("Widerøe"),
    AY("Finnair"), BT("Air Baltic"), D8("Norwegian"), DX("DAT Danish Air Transport"),
    LTR("Lufttransport"), RC("Atlantic Airways"), FR("Ryanair"), EJU("EasyJet"),
    KL("KLM"), W2("Flexflight");

    private String airlineName;

    AirlineIATA(String airlineName) {
        this.airlineName = airlineName;
    }

    }
