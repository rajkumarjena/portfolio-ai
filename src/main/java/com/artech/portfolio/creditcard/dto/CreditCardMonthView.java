package com.artech.portfolio.creditcard.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MPC_V1 - every card of the client for one planner month, with the cycle that
 * ends in it. {@code total} is exactly the figure the Monthly Planner shows on
 * its "Credit Card Bills" line for that month.
 */
@Data
public class CreditCardMonthView {

    private LocalDate month;
    private String monthLabel;
    private List<CreditCardView> cards = new ArrayList<>();
    private double total;
}
