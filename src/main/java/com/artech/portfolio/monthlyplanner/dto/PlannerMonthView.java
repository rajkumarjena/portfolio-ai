package com.artech.portfolio.monthlyplanner.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MP_V1 - one plan month: its earnings, its spending and the two totals. */
@Data
public class PlannerMonthView {

    /** First day of the month the figures belong to. */
    private LocalDate month;
    /** "November 2026" - ready to render, the page never formats dates itself. */
    private String monthLabel;

    private List<PlannerLineView> earnings = new ArrayList<>();
    private List<PlannerLineView> spending = new ArrayList<>();

    private double earningsTotal;
    private double spendingTotal;
    private double net;

    /**
     * MP_V1 #4: the recurrence options (key -> label) the page renders. They come
     * from MonthlyPlannerConstants so the screen has no duplicated wording.
     */
    private Map<String, String> frequencyOptions = new LinkedHashMap<>();
}
