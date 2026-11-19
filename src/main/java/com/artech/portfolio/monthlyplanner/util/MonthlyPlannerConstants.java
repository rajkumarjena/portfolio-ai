package com.artech.portfolio.monthlyplanner.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MP_V1 - Monthly Planner vocabulary. Nothing about the planner is hard coded in
 * the service or the pages: the categories, their group, their display label,
 * the recurrence options and the SIP sources all live here.
 *
 * Earnings repeat or are entered month by month (MP_V1 #4); spending is either a
 * per-month obligation the client types in or a figure auto-populated from
 * another module (MP_V1 #5).
 */
public final class MonthlyPlannerConstants {

    private MonthlyPlannerConstants() {
    }

    // ------------------------------------------------------------------ groups

    /** MP_V1 #2: the two halves of the planner. */
    public static final String KIND_EARNING = "EARNING";
    public static final String KIND_SPENDING = "SPENDING";

    public static final String GROUP_EARNINGS = "Earnings";
    public static final String GROUP_SPENDING = "Spending (Obligations)";

    // ---------------------------------------------------------- earning keys

    public static final String SALARY = "SALARY";
    public static final String FREELANCE = "FREELANCE";
    public static final String SIDE_HUSTLE = "SIDE_HUSTLE";
    /** MP_V1 #9: the catch-all earning for anything the defined types do not cover. */
    public static final String MISC_EARNING = "MISC_EARNING";

    // --------------------------------------------------------- spending keys

    public static final String SHARES = "SHARES";
    public static final String BONDS = "BONDS";
    public static final String PPF = "PPF";
    public static final String EPF = "EPF";
    public static final String NPS = "NPS";
    public static final String SMALLCASE = "SMALLCASE";
    public static final String CREDIT_CARD = "CREDIT_CARD";
    public static final String LIC = "LIC";
    public static final String HOME_LOAN = "HOME_LOAN";
    public static final String FUEL = "FUEL";
    public static final String BOND_SIP = "BOND_SIP";
    public static final String MF_SIP = "MF_SIP";
    /** MP_V1 #9: the catch-all expense for anything the defined types do not cover. */
    public static final String MISC_SPENDING = "MISC_SPENDING";

    /**
     * MP_V3 #2: a client may add several Miscellaneous rows in one month. The
     * first one keeps the category constant above; every additional row stores
     * a stable suffix after this separator ("MISC_EARNING#2"), so the existing
     * one-row-per-category unique key still protects the ordinary categories.
     */
    public static final String MISC_SEPARATOR = "#";

    /** MP_V1 #2: every earning category, in screen order. */
    public static final Map<String, String> EARNINGS = immutable(Map.ofEntries(
            Map.entry(SALARY, "Salary"),
            Map.entry(FREELANCE, "Freelance Payouts"),
            Map.entry(SIDE_HUSTLE, "Side-Hustle Incomes"),
            Map.entry(MISC_EARNING, "Miscellaneous")));

    /** MP_V1 #2: every spending (obligation) category, in screen order. */
    public static final Map<String, String> SPENDING = immutable(Map.ofEntries(
            Map.entry(SHARES, "Buying Shares"),
            Map.entry(BONDS, "Buying Bonds"),
            Map.entry(PPF, "PPF"),
            Map.entry(EPF, "EPF"),
            Map.entry(NPS, "NPS"),
            Map.entry(SMALLCASE, "Smallcase"),
            Map.entry(CREDIT_CARD, "Credit Card Bills"),
            Map.entry(LIC, "LIC Insurance Premium"),
            Map.entry(HOME_LOAN, "Home Loan EMI"),
            Map.entry(FUEL, "Fuel & Transportation"),
            Map.entry(BOND_SIP, "Bond SIP"),
            Map.entry(MF_SIP, "Mutual Fund SIP"),
            Map.entry(MISC_SPENDING, "Miscellaneous")));

    /**
     * MP_V1 #5: spending the planner fills in from its own module. When the
     * source module has data for the month the line is read only ("adjust it on
     * that screen"); when it has none, the client's own monthly figure is used.
     * Home Loan EMI is NOT a SIP - it is a repeated monthly entry with an end
     * date, like a salary, so it is not linked to another screen.
     */
    public static final Map<String, String> SOURCE_LINKS = immutable(Map.ofEntries(
            Map.entry(BOND_SIP, "/bonds/bond-sip.html"),
            Map.entry(MF_SIP, "/mutualfunds/mf-sip-form.html"),
            Map.entry(CREDIT_CARD, "credit-cards")));

    /** MP_V1 #4: the categories that repeat by default until the client says otherwise. */
    public static final Set<String> REPEATING_BY_DEFAULT = Set.of(SALARY, HOME_LOAN);

    // -------------------------------------------------------------- recurrence

    public static final String FREQUENCY_WEEKLY = "WEEKLY";
    public static final String FREQUENCY_MONTHLY = "MONTHLY";
    public static final String FREQUENCY_QUARTERLY = "QUARTERLY";
    public static final String FREQUENCY_HALF_YEARLY = "HALF_YEARLY";
    public static final String FREQUENCY_YEARLY = "YEARLY";
    /**
     * MP_V1 #9: a single dated amount - a purchase, a fee, a bonus - that exists
     * in the month of its own date and in no other month. Its start and end date
     * are the same date.
     */
    public static final String FREQUENCY_ONE_TIME = "ONE_TIME";

    /** MP_V1 #4: "repeating monthly/weekly/yearly" - the recurrence options. */
    public static final Map<String, String> FREQUENCIES = immutable(Map.ofEntries(
            Map.entry(FREQUENCY_MONTHLY, "Every month"),
            Map.entry(FREQUENCY_QUARTERLY, "Every quarter"),
            Map.entry(FREQUENCY_HALF_YEARLY, "Half-yearly"),
            Map.entry(FREQUENCY_YEARLY, "Every year"),
            Map.entry(FREQUENCY_WEEKLY, "Every week"),
            Map.entry(FREQUENCY_ONE_TIME, "One time (single date)")));

    public static final String FREQUENCY_DEFAULT = FREQUENCY_MONTHLY;

    /** MP_V1 #4: how many months apart two repeats of a frequency are. */
    public static final int MONTHS_MONTHLY = 1;
    public static final int MONTHS_QUARTERLY = 3;
    public static final int MONTHS_HALF_YEARLY = 6;
    public static final int MONTHS_YEARLY = 12;

    /** Days in a week - a weekly entry repeats on one weekday of every week. */
    public static final int DAYS_IN_WEEK = 7;

    // ------------------------------------------------------------------ source

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_BOND_SIP = "BOND_SIP";
    public static final String SOURCE_MF_SIP = "MF_SIP";
    /** The history row was supplied by another module (a SIP, the credit cards). */
    public static final String SOURCE_AUTO = "AUTO";

    /** The plan month is always the first day of the calendar month. */
    public static final int MONTH_PATTERN_LENGTH = 7; // "yyyy-MM"

    /** MP_V1 #8: how many months the history API returns when no window is asked for. */
    public static final int MONTHS_IN_HISTORY_WINDOW = 12;

    public static String kindOf(String category) {
        if (EARNINGS.containsKey(category) || isMisc(category, MISC_EARNING)) {
            return KIND_EARNING;
        }
        if (SPENDING.containsKey(category) || isMisc(category, MISC_SPENDING)) {
            return KIND_SPENDING;
        }
        return null;
    }

    public static String labelOf(String category) {
        if (isMiscCategory(category)) {
            return "Miscellaneous";
        }
        String label = EARNINGS.get(category);
        return label != null ? label : SPENDING.get(category);
    }

    /** MP_V3 #2: the base category for a kind's Miscellaneous rows. */
    public static String miscBaseFor(String kind) {
        return KIND_EARNING.equals(kind) ? MISC_EARNING : MISC_SPENDING;
    }

    /** True for the base Miscellaneous category and every additional row of it. */
    public static boolean isMiscCategory(String category) {
        return isMisc(category, MISC_EARNING) || isMisc(category, MISC_SPENDING);
    }

    /** True for the base category of {@code kind} and its additional rows. */
    public static boolean isMiscCategory(String category, String kind) {
        return isMisc(category, miscBaseFor(kind));
    }

    private static boolean isMisc(String category, String base) {
        return category != null
                && (category.equals(base) || category.startsWith(base + MISC_SEPARATOR));
    }

    public static List<String> categories(String kind) {
        return KIND_EARNING.equals(kind) ? List.copyOf(EARNINGS.keySet())
                : List.copyOf(SPENDING.keySet());
    }

    public static String frequencyOr(String raw) {
        if (raw == null) {
            return FREQUENCY_DEFAULT;
        }
        String key = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        return FREQUENCIES.containsKey(key) ? key : FREQUENCY_DEFAULT;
    }

    /** The month step of a frequency: 1, 3, 6 or 12 (weekly repeats every week). */
    public static int monthsBetweenRepeats(String frequency) {
        return switch (frequencyOr(frequency)) {
            case FREQUENCY_QUARTERLY -> MONTHS_QUARTERLY;
            case FREQUENCY_HALF_YEARLY -> MONTHS_HALF_YEARLY;
            case FREQUENCY_YEARLY -> MONTHS_YEARLY;
            default -> MONTHS_MONTHLY;
        };
    }

    private static Map<String, String> immutable(Map<String, String> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
