package com.artech.portfolio.creditcard.util;

/**
 * MPC_V1 - Credit card billing cycles and the figures the Monthly Planner takes
 * from them. Nothing about the module is hard coded in the service, the
 * controller or the popup: the limits, the default cycle day and the way a
 * cycle's amount was arrived at live here.
 */
public final class CreditCardConstants {

    private CreditCardConstants() {
    }

    /** A billing cycle ends on a day of the month. */
    public static final int MIN_BILLING_DAY = 1;
    public static final int MAX_BILLING_DAY = 31;

    /** A card added without a chosen day ends its cycle on the first of the month. */
    public static final int DEFAULT_BILLING_DAY = 1;

    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_DESCRIPTION_LENGTH = 120;
    public static final int MAX_NOTE_LENGTH = 250;

    /** "yyyy-MM" - the planner month the popup is opened for. */
    public static final int MONTH_PATTERN_LENGTH = 7;

    /**
     * How the planned figure of one billing cycle was arrived at: the bill the
     * client entered for that cycle, the purchases captured in it, or nothing.
     */
    public static final String AMOUNT_BILL = "BILL";
    public static final String AMOUNT_PURCHASES = "PURCHASES";
    public static final String AMOUNT_NONE = "NONE";
}
