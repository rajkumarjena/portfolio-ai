package com.artech.portfolio.creditcard.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MPC_V1 - one card and the billing cycle that ends in the planner month: its
 * dates, the purchases captured in it, whether a bill was entered and what the
 * Monthly Planner is given for the month.
 */
@Data
public class CreditCardView {

    private Long id;
    private String name;
    private int billingCycleDay;
    private String note;

    private LocalDate cycleStart;
    private LocalDate cycleEnd;
    /** "6 Oct 2026 - 5 Nov 2026", ready to render. */
    private String cycleLabel;

    private List<CreditCardPurchaseView> purchases = new ArrayList<>();
    private double purchaseTotal;
    private int purchaseCount;

    /** The bill the client entered for the cycle (0 when none was entered). */
    private double billAmount;
    private boolean billEntered;

    /** What this card plans in the month: the bill when entered, else the purchases. */
    private double amount;
    /** BILL / PURCHASES / NONE - how {@code amount} was arrived at. */
    private String amountSource;
}
