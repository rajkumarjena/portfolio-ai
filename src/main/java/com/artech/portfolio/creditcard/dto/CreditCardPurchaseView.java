package com.artech.portfolio.creditcard.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * MPC_V1 - one purchase as the Credit Card Bills Entry screen lists it.
 * MP_V5 #3: {@code billMonth} is the month the transaction is billed in, and the
 * flags tell the screen which of the two displayed month options is chosen and
 * whether the next-month option may be chosen at all.
 */
@Data
public class CreditCardPurchaseView {

    private Long id;
    private LocalDate purchaseDate;
    private String description;
    private double amount;

    /** First day of the month this transaction is billed in. */
    private LocalDate billMonth;

    /** MP_V6 #2: the "bill in the selected month" option is chosen. */
    private boolean inSelectedMonth;
    /** MP_V6 #2: the "bill in the month after it" option is chosen. */
    private boolean inNextMonth;
    /**
     * MP_V5 #3.ii: true when the cycle end plus grace already falls in the
     * selected month, so the transaction cannot be pushed to the next one.
     */
    private boolean nextDisabled;

    /** MP_V5 #3.vii: older than one month before the current month - no "X". */
    private boolean removable;
}
