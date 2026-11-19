package com.artech.portfolio.monthlyplanner.dto;

import lombok.Data;

@Data
public class PlannerEntryRequest {
    private String category;
    private Double amount;
    private boolean recurring;
    private String frequency;
    private String startDate;
    private String endDate;
    private String note;
    /** MP_V2 #6: whether the spending bill/obligation has been paid. */
    private Boolean paid;
}