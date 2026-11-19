package com.artech.portfolio.monthlyplanner.dto;

import lombok.Data;

import java.time.LocalDate;

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
    private String source;
    /** MP_V2 #6: whether the spending bill/obligation was paid. */
    private boolean paid;
}