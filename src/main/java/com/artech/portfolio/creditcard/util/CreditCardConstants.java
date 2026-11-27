package com.artech.portfolio.creditcard.util;

/**
 * MPC_V1 - Credit card billing cycles and the figures the Monthly Planner takes
 * from them. Nothing about the module is hard coded in the service, the
 * controller or the popup: the limits and the defaults live here.
 *
 * <p>MP_V5: a card also carries a grace period, and the month a transaction is
 * billed in is the month of its cycle end plus that grace period.</p>
 */
public final class CreditCardConstants {

    private CreditCardConstants() {
    }

    /** A billing cycle ends on a day of the month. */
    public static final int MIN_BILLING_DAY = 1;
    public static final int MAX_BILLING_DAY = 31;

    /** A card added without a chosen day ends its cycle on the first of the month. */
    public static final int DEFAULT_BILLING_DAY = 1;

    /** MP_V5 #2: days after the statement (the day after the cycle end) before the amount is due. */
    public static final int MIN_GRACE_DAYS = 0;
    public static final int MAX_GRACE_DAYS = 60;
    public static final int DEFAULT_GRACE_DAYS = 0;

    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_DESCRIPTION_LENGTH = 120;
    public static final int MAX_NOTE_LENGTH = 250;

    /** "yyyy-MM" - the planner month the popup is opened for. */
    public static final int MONTH_PATTERN_LENGTH = 7;

    /**
     * MP_V5 #3.vii: transactions of the previous month can still be removed, but
     * nothing older than that - in November the October rows can be deleted and
     * the September ones cannot.
     */
    public static final int MONTHS_BEFORE_CURRENT_REMOVABLE = 1;
}
