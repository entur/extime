package no.rutebanken.extime.model;

import java.util.EnumSet;

public enum AirlineDesignator {

    DY, SK, WF, M3, FI, OS;

    public static EnumSet<AirlineDesignator> commonDesignators = EnumSet.of(DY, SK, WF);
    public static EnumSet<AirlineDesignator> rareDesignators = EnumSet.of(M3, FI, OS);

}
