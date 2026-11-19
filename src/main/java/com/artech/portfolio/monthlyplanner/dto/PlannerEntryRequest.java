package com.artech.portfolio.monthlyplanner.dto;

import lombok.Data;

/** MP_V1 - one submitted figure of the Save action. */
@Data
public class PlannerEntryRequest {
    private String category;
    private Double amount;
    private boolean recurring;
    private String frequency;
    /** First date the entry applies from ("yyyy-MM-dd" or "yyyy-MM"); empty = the plan month. */
    private String startDate;
    /** Last month the repeat applies to ("yyyy-MM"), empty/absent = open ended. */
    private String endDate;
    private String note;
}
