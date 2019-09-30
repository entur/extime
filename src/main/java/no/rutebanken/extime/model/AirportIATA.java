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
    DLD, // Dagali
    EVE, // Harstad/Narvik lufthavn
    FAN, // Farsund
    FDE, // Førde lufthamn
    FRO, // Florø lufthamn
    GLL, // Gol

    HAA, // Hasvik lufthavn
    HAU, // Haugesund lufthavn
    HFT, // Hammerfest lufthavn
    HMR, // Hamar
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
    NTB, // Notodden
    NVK, // Narvik lufthavn
    OLA, // Ørland
    OSL, // Oslo lufthavn
    OSY, // Namsos lufthavn
    RET, // Røst lufthavn
    RRS, // Røros lufthavn
   // RYG, // Rygge
    RVK, // Rørvik lufthavn
    SDN, // Sandane lufthamn
    SKE, // Skien
    SKN, // Stokmarknes lufthavn
    SOG, // Sogndal lufthamn
    SOJ, // Sørkjosen lufthavn
    SRP, // Stord
    SSJ, // Sandnessjøen lufthavn
    SVG, // Stavanger lufthavn
    SVJ, // Svolvær lufthavn
    TOS, // Tromsø lufthavn
    TRD, // Trondheim lufthavn
    TRF, // Sandefjord lufthavn, Torp
    VAW, // Vardø lufthavn
    VDB, // Fagernes lufthamn
    VDS, // Vadsø lufthavn
    VRY; // Værøy helikopterhavn


    /*
    ARN; // Stockholm Arlanda Lufthavn

    GOT, // Göteborg Landvetter flygplats

    HEL, // Helsingfors-Vanda lufthavn
    CPH, // Københavns lufthavn
    VNO, // Vilnius internasjonale lufthavn
    TLL, // Lennart Meri Tallinn lufthavn
    RIX, // Riga internasjonale lufthavn
    AAL, // Aalborg Lufthavn
    BLL, // Billund Lufthavn
    EBJ, // Esbjerg lufthavn
    FAE, // Vága Floghavn

    CDG, // Paris-Charles-de-Gaulle Lufthavn
    ORY, // Paris-Orly Lufthavn
    KEF, // Reykjavík-Keflavík Lufthavn
    AMS, // Amsterdam-Schiphol lufthavn
    GDN, // Gdańsk Lech Wałęsa lufthavn
    KRK, // Johannes Paul II lufthavn Kraków-Balice
    WAW, // Frédéric Chopin lufthavn Warszawa-Okęcie
    HAM, // Hamburg lufthavn
    SXF, // Berlin Schönefeld lufthavn
    TXL, // Berlin-Tegel internasjonale lufthavn
    ABZ, // Aberdeen lufthavn
    EDI, // Edinburgh lufthavn
    LGW, // London Gatwick lufthavn
    LHR, // London Heathrow lufthavn
    LTN, // London Luton lufthavn
    STN, // London Stansted lufthavn
    SVO, // Moska-Sjeremetjevo internasjonale lufthavn
    FRA, // Frankfurt internasjonale lufthavn
    KUN, // Kaunas internasjonale lufthavn
    BRU, // Brussel-Nationaal luchthaven
    LUZ; // Lublin lufthavn
    */

    public static EnumSet<AirportIATA> LARGE_SIZED_AIRPORTS = EnumSet.of(OSL);
    public static EnumSet<AirportIATA> MEDIUM_SIZED_AIRPORTS = EnumSet.of(BGO, BOO, SVG, TRD);

}
