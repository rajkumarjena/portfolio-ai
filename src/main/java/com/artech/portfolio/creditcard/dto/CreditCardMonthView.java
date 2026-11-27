package com.artech.portfolio.creditcard.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MPC_V1 - every card of the client for one planner month.
 *
 * <p>MP_V6: the screen shows the selected month and the month after it, and the
 * client may pick any month (past or future), so the pair is relative to the
 * selection instead of the system clock. {@code defaultMonth} is the month the
 * screen opens on - the next one once every card's grace date of the current
 * month has passed - and {@code editable} says whether that pair's bill months
 * may still be changed (a past month is read only).</p>
 */
@Data
public class CreditCardMonthView {

    /** The month the figures below describe. */
    private LocalDate month;
    private String monthLabel;

    /** MP_V6 #2: the selected month - the first of the two displayed months. */
    private LocalDate selectedMonth;
    private String selectedMonthLabel;
    /** MP_V6 #2: the month after the selected one. */
    private LocalDate nextMonth;
    private String nextMonthLabel;

    /** MP_V6 #3: the month the screen opens on without a choice. */
    private LocalDate defaultMonth;
    private String defaultMonthLabel;

    /** MP_V6: false for a past month - its bills are only displayed. */
    private boolean editable;

    private List<CreditCardView> cards = new ArrayList<>();

    /** The selected month's total (the planner's figure for it). */
    private double total;

    /** MP_V6 #2: every card's transactions billed in the selected month. */
    private double selectedBillTotal;
    /** MP_V6 #2: every card's transactions billed in the month after it. */
    private double nextBillTotal;
}
