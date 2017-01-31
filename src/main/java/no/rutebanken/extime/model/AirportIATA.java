package no.rutebanken.extime.model;

import java.util.EnumSet;

public enum AirportIATA {

    AES, // Ålesund lufthavn
    ALF, // Alta lufthavn
    ANX, // Andøya lufthavn
    BDU, // Bardufoss lufthavn
    BGO, // Bergen lufthavn
    BJF, // Båtsfjord lufthavn
    BNN, // Brønnøysund lufthavn
    BOO, // Bodø lufthavn
    BVG, // Berlevåg lufthavn
    EVE, // Harstad/Narvik lufthavn
    FDE, // Førde lufthamn
    FRO, // Florø lufthamn
    HAA, // Hasvik lufthavn
    HAU, // Haugesund lufthavn
    HFT, // Hammerfest lufthavn
    HOV, // Ørsta-Volda lufthamn
    HVG, // Honningsvåg lufthavn
    KKN, // Kirkenes lufthavn
    KRS, // Kristiansand lufthavn
    KSU, // Kristiansund lufthavn
    LKL, // Lakselv lufthavn
    LKN, // Leknes lufthavn
    LYR, // Svalbard lufthavn
    MEH, // Mehamn lufthavn
    MJF, // Mosjøen lufthavn
    MOL, // Molde lufthavn
    MQN, // Mo i Rana lufthavn
    NVK, // Narvik lufthavn
    OSL, // Oslo lufthavn
    OSY, // Namsos lufthavn
    RET, // Røst lufthavn
    RRS, // Røros lufthavn
    RVK, // Rørvik lufthavn
    SDN, // Sandane lufthamn
    SKN, // Stokmarknes lufthavn
    SOG, // Sogndal lufthamn
    SOJ, // Sørkjosen lufthavn
    SSJ, // Sandnessjøen lufthavn
    SVG, // Stavanger lufthavn
    SVJ, // Svolvær lufthavn
    TOS, // Tromsø lufthavn
    TRD, // Trondheim lufthavn
    VAW, // Vardø lufthavn
    VDB, // Fagernes lufthamn
    VDS, // Vadsø lufthavn
    VRY;  // Værøy helikopterhavn

    public static EnumSet<AirportIATA> LARGE_SIZED_AIRPORTS = EnumSet.of(OSL);
    public static EnumSet<AirportIATA> MEDIUM_SIZED_AIRPORTS = EnumSet.of(BGO, BOO, SVG, TRD);

}
