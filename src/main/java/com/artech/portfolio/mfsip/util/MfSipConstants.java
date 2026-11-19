package com.artech.portfolio.mfsip.util;

import java.util.List;

/**
 * MF.md 1/7/11 - Mutual Fund SIP / investment registration vocabulary. The entry
 * screen, the SIP list and the Monthly Planner all read these values, so the
 * wording lives in exactly one place.
 */
public final class MfSipConstants {

    private MfSipConstants() {
    }

    /** A running SIP or a single purchase (MF.md 11). */
    public static final String TYPE_SIP = "SIP";
    public static final String TYPE_ONE_TIME = "ONE_TIME";
    public static final List<String> REGISTRATION_TYPES = List.of(TYPE_SIP, TYPE_ONE_TIME);

    /** Period between two instalments of a SIP. */
    public static final String WEEKLY = "WEEKLY";
    public static final String MONTHLY = "MONTHLY";
    public static final String QUARTERLY = "QUARTERLY";
    public static final String HALF_YEARLY = "HALF_YEARLY";
    public static final String YEARLY = "YEARLY";
    public static final List<String> SIP_TYPES =
            List.of(WEEKLY, MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY);
    public static final String SIP_TYPE_DEFAULT = MONTHLY;

    /** Registration lifecycle shown on the SIP list (MF.md 12). */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final List<String> STATUSES =
            List.of(STATUS_ACTIVE, STATUS_PAUSED, STATUS_CANCELLED, STATUS_CLOSED);

    /** Redemption modes of the Redeem dialog (MF.md 10). */
    public static final String REDEEM_UNITS = "UNITS";
    public static final String REDEEM_AMOUNT = "AMOUNT";
    public static final String REDEEM_FULL = "FULL";

    /** Day-of-month bounds of a SIP trigger. */
    public static final int MIN_SIP_DAY = 1;
    public static final int MAX_SIP_DAY = 31;

    /** A holding smaller than this is treated as fully redeemed. */
    public static final double ZERO_UNITS = 0.0001;

    public static String registrationTypeOr(String raw) {
        String key = upper(raw);
        return REGISTRATION_TYPES.contains(key) ? key : TYPE_SIP;
    }

    public static String sipTypeOr(String raw) {
        String key = upper(raw);
        return SIP_TYPES.contains(key) ? key : SIP_TYPE_DEFAULT;
    }

    public static String statusOr(String raw) {
        String key = upper(raw);
        return STATUSES.contains(key) ? key : null;
    }

    private static String upper(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    }
}
