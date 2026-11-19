package com.artech.portfolio.monthlyplanner.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PlannerLineView {

    private String category;
    private String label;
    private String kind;

    private double amount;

    private double entryAmount;

    private String frequency;
    private boolean recurring;

    private boolean editable;

    private boolean auto;

    private boolean inherited;
    private LocalDate inheritedFrom;

    private LocalDate startDate;

    private LocalDate endDate;

    private boolean counted = true;

    private int occurrences = 1;

    private String sourceLink;
    private String note;

    /** MP_V3 #2: a Miscellaneous row the client may duplicate with its own note. */
    private boolean misc;

    /** MP_V2 #6: whether the spending bill/obligation has been paid. */
    private boolean paid;
}