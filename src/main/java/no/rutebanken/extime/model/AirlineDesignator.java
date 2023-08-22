package no.rutebanken.extime.model;

import java.util.EnumSet;
import java.util.Set;

public enum AirlineDesignator {

    DY, SK, WF, M3, LTR, DX, FI, AY, BT, D8, RC, FR, EJU, KL, W2,
    AF, BA, LH, LO, SN, SU, W6, FL, FS;

    public static final Set<AirlineDesignator> commonDesignators = EnumSet.of(DY, SK, WF, M3, LTR, DX, FI, AY, BT, D8,
            RC, FR, EJU, KL, W2, AF, BA, LH, LO, SN, SU, W6, FL, FS);

}
