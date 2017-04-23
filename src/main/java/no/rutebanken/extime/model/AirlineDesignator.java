package no.rutebanken.extime.model;

import java.util.EnumSet;

public enum AirlineDesignator {

    DY, SK, WF, M3, LTR, VF;

    public static EnumSet<AirlineDesignator> commonDesignators = EnumSet.of(DY, SK, WF, M3, LTR, VF);

}
