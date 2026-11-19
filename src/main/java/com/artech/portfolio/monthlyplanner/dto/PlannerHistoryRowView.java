package com.artech.portfolio.monthlyplanner.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * MP_V1 #8 - one captured figure of one month, as the history API returns it.
 * The analytics screen that will be built later reads exactly these rows.
 */
@Data
public class PlannerHistoryRowView {

    private LocalDate month;
    private String monthLabel;
    private String category;
    private String label;
    private String kind;
    private double amount;
    private double entryAmount;
    private int occurrences;
    private String frequency;
    private boolean recurring;
    /** MANUAL, or AUTO when another module supplied the figure. */
    private String source;
}
