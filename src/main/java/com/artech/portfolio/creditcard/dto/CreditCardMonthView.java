package com.artech.portfolio.creditcard.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MPC_V1 - every card of the client for one planner month.
 *
 * <p>MP_V5 #4: the screen also shows the system current and next month with the
 * sum of every transaction billed in them; {@code total} remains the planner
 * month's figure (requirement #5).</p>
 */
@Data
public class CreditCardMonthView {

    private LocalDate month;
    private String monthLabel;

    /** MP_V5 #4: the system's current month and the month after it. */
    private LocalDate currentMonth;
    private String currentMonthLabel;
    private LocalDate nextMonth;
    private String nextMonthLabel;

    private List<CreditCardView> cards = new ArrayList<>();

    /** The planner month's total - requirement #5. */
    private double total;

    /** MP_V5 #4: every card's transactions billed in the current month. */
    private double currentBillTotal;
    /** MP_V5 #4: every card's transactions billed in the next month. */
    private double nextBillTotal;
}
