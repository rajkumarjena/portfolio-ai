package com.artech.portfolio.bondsip.util;

import java.util.List;

/**
 * BONDBP_V7 status lifecycle: #3 CREATED on creation, #4 INPROGRESS when the
 * scheduler executes it on the (weekday-adjusted) SIP date, #7 EXECUTED once
 * the Bond Name is associated on the Bond SIP Transaction screen.
 *
 * Frequency (like a bond's coupon frequency): how often a Bond SIP instalment
 * comes due - WEEKLY, MONTHLY, QUARTERLY, HALF-YEARLY or YEARLY. Each due
 * instalment auto-creates one Bond SIP Transaction record (INPROGRESS); a SIP
 * entered with a past start date is backfilled with every instalment due from
 * its SIP date up to today.
 */
public class BondSipConstants {
    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_INPROGRESS = "INPROGRESS";
    public static final String STATUS_EXECUTED = "EXECUTED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String FREQUENCY_WEEKLY = "WEEKLY";
    public static final String FREQUENCY_MONTHLY = "MONTHLY";
    public static final String FREQUENCY_QUARTERLY = "QUARTERLY";
    public static final String FREQUENCY_HALF_YEARLY = "HALF-YEARLY";
    public static final String FREQUENCY_YEARLY = "YEARLY";

    /** The drop-down / validation list, in screen order. */
    public static final List<String> FREQUENCIES = List.of(
            FREQUENCY_WEEKLY, FREQUENCY_MONTHLY, FREQUENCY_QUARTERLY,
            FREQUENCY_HALF_YEARLY, FREQUENCY_YEARLY);
}
