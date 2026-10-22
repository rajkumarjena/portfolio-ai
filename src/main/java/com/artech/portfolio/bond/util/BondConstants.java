package com.artech.portfolio.bond.util;

public class BondConstants {
    public static final String BOND = "bond";
    public static final String ISSUER = "issuer";
    public static final String COUPON_FREQUENCY = "coupon-frequency";
    public static final String CREDIT_RATING = "credit-rating";

    // ---------------------------------------------------------- BONDBP_V9 #2
    /** Bond record lifecycle shown on the Bond Records screen. */
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_EXECUTED = "EXECUTED";

    /** BONDBP_V9 #2 bond origin: direct entry vs Bond SIP instalment. */
    public static final String INVESTMENT_TYPE_ONE_TIME = "One Time Investment";
    public static final String INVESTMENT_TYPE_SIP = "SIP Investment";

    /**
     * BONDBP_V9 #8: what the Bond Details Entry screen shows for a record that
     * came from a Bond SIP instalment (the list screen keeps the stored
     * "SIP Investment" wording of rule 2).
     */
    public static final String INVESTMENT_TYPE_SIP_DISPLAY = "SIP Transaction";

    /** BONDBP_V9 #9 screen sections of the Bond Records screen. */
    public static final String SECTION_ACTIVE = "active";
    public static final String SECTION_EXPIRED = "expired";
}
