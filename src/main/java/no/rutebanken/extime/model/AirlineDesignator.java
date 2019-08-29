package no.rutebanken.extime.model;

import java.util.EnumSet;

public enum AirlineDesignator {

    DY, SK, WF, M3, LTR, VF, DX, FI, AY, BT, D8, RC, FR, EJU, KL, W2,
    AF, BA, LH, LM, LO, SN, SU, W6, DK;

    public static EnumSet<AirlineDesignator> commonDesignators = EnumSet.of(DY, SK, WF, M3, LTR, VF, DX, FI, AY, BT, D8,
            RC, FR, EJU, KL, W2, AF, BA, LH, LM, LO, SN, SU, W6, DK);

}
