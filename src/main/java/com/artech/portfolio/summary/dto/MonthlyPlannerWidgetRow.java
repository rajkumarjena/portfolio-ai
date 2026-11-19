package com.artech.portfolio.summary.dto;

import lombok.Data;

/**
 * MP_V4 #4/#5: one earning or spending row of the Monthly Planner Summary
 * widget, carrying the signed-in client's and the family's figures for the
 * current and the next month. A row is present only when at least one of the
 * four figures is greater than zero.
 */
@Data
public class MonthlyPlannerWidgetRow {

    /** Planner category, e.g. SALARY or MISC_EARNING. */
    private String category;

    /** Human caption, e.g. "Salary" or "Miscellaneous". */
    private String label;

    /** MP_V4 #6: the single aggregated Miscellaneous row of its section. */
    private boolean misc;

    /** MP_V4 #4: Self - current month and next month. */
    private double selfCurrent;
    private double selfNext;

    /** MP_V4 #4/#7: Family - the client plus every associated family member. */
    private double familyCurrent;
    private double familyNext;
}
