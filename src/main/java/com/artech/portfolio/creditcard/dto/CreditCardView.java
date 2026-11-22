package com.artech.portfolio.creditcard.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MPC_V1 - one card and its billing cycle of the planner month.
 *
 * <p>MP_V5 #3: the purchases carry the two month options, and the card sums its
 * own transactions for the current and the next month. {@code amount} stays what
 * the Monthly Planner plans for its month (every transaction billed in it).</p>
 */
@Data
public class CreditCardView {

    private Long id;
    private String name;
    private int billingCycleDay;
    /** MP_V5 #2: days after the cycle end before the amount is due. */
    private int gracePeriodDays;
    private String note;

    private LocalDate cycleStart;
    private LocalDate cycleEnd;
    /** "6 Oct 2026 - 5 Nov 2026", ready to render. */
    private String cycleLabel;

    /** MP_V5 #3: the transactions billable in the current or the next month. */
    private List<CreditCardPurchaseView> purchases = new ArrayList<>();
    private int purchaseCount;

    /** MP_V5 #3.vi: this card's total billed in the current month. */
    private double currentBillTotal;
    /** MP_V5 #3.vi: this card's total billed in the next month. */
    private double nextBillTotal;

    /** What this card plans in the planner month - transactions billed in it. */
    private double amount;
}
