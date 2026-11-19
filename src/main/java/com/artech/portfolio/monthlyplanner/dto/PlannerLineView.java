package com.artech.portfolio.monthlyplanner.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * MP_V1 - one line of a monthly planner screen: a category, the figure for the
 * month and how that figure got there (typed in, carried over from a recurring
 * entry, or auto-populated from another module).
 */
@Data
public class PlannerLineView {

    private String category;
    private String label;
    private String kind;

    /** What the line contributes to this month (weekly entries: the month's actual weeks). */
    private double amount;

    /**
     * The figure as entered - what the Amount box shows. It differs from
     * {@code amount} for a weekly entry (paid per week) and for an entry whose
     * start date is in a later month (kept, but not counted yet).
     */
    private double entryAmount;

    /** MONTHLY / QUARTERLY / HALF_YEARLY / YEARLY / WEEKLY (MP_V1 #4). */
    private String frequency;
    private boolean recurring;

    /** False when the figure comes from another module (MP_V1 #5) - adjust it there. */
    private boolean editable;

    /** True when the figure was auto-populated from the source module. */
    private boolean auto;

    /** True when the figure was carried over from an earlier recurring entry (#4). */
    private boolean inherited;
    private LocalDate inheritedFrom;

    /** First date the entry applies from (editable). */
    private LocalDate startDate;

    /** Last month the repeat applies to (editable); null means open ended. */
    private LocalDate endDate;

    /**
     * False when the entry starts in a later month: the figure is kept on the
     * line but it is not part of this month's total.
     */
    private boolean counted = true;

    /** How many times a weekly entry falls in this month (1 for every other frequency). */
    private int occurrences = 1;

    /** The screen the auto-populated figure is maintained on (MP_V1 #5). */
    private String sourceLink;
    private String note;
}
