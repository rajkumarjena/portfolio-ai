package com.artech.portfolio.creditcard.util;

import java.time.LocalDate;

/**
 * MPC_V1 - one billing cycle of a card per month: the cycle ends on the card's
 * billing day and starts the day after the previous cycle ended. A card whose
 * cycle day is 5 bills 6 October - 5 November 2026.
 *
 * <p>MP_V5: the cycle a purchase belongs to is the cycle containing its own
 * date, and the month it is billed in is the month of that cycle's end plus the
 * card's grace period.</p>
 */
public final class CreditCardCycle {

    private CreditCardCycle() {
    }

    /**
     * The date the card's billing cycle of {@code month} ends. A 31st cycle day
     * ends on the last day of a shorter month, exactly like the banks do.
     */
    public static LocalDate endOf(int billingCycleDay, LocalDate month) {
        LocalDate first = month.withDayOfMonth(1);
        int day = Math.min(Math.max(billingCycleDay, CreditCardConstants.MIN_BILLING_DAY),
                first.lengthOfMonth());
        return first.withDayOfMonth(day);
    }

    /** The first day of the cycle that ends on {@code cycleEnd}. */
    public static LocalDate startOf(int billingCycleDay, LocalDate cycleEnd) {
        return endOf(billingCycleDay, cycleEnd.withDayOfMonth(1).minusMonths(1)).plusDays(1);
    }

    /**
     * MP_V5: the end of the cycle that contains {@code purchaseDate}. A purchase
     * on 20 October with a 5th cycle day belongs to the cycle ending 5 November.
     */
    public static LocalDate endOfPurchase(int billingCycleDay, LocalDate purchaseDate) {
        LocalDate cycleEnd = endOf(billingCycleDay, purchaseDate.withDayOfMonth(1));
        if (purchaseDate.isAfter(cycleEnd)) {
            cycleEnd = endOf(billingCycleDay,
                    purchaseDate.withDayOfMonth(1).plusMonths(1));
        }
        return cycleEnd;
    }
}
