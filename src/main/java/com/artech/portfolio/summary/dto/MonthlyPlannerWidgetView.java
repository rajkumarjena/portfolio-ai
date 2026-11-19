package com.artech.portfolio.summary.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MP_V4: payload of the Monthly Planner Summary widget. Like the other
 * Portfolio Summary cards, all figures are already computed on the server; the
 * page only formats them.
 *
 * <p>The four figures are Self current month, Self next month, Family current
 * month and Family next month. Family is the signed-in client plus every
 * associated family member (#7).</p>
 */
@Data
public class MonthlyPlannerWidgetView {

    /** The date the widget was built for ("As on ..."). */
    private LocalDate asOf;

    private LocalDate currentMonth;
    private String currentMonthLabel;

    private LocalDate nextMonth;
    private String nextMonthLabel;

    /** Earnings and spending rows, each with the four figures (#4/#5). */
    private List<MonthlyPlannerWidgetRow> earnings = new ArrayList<>();
    private List<MonthlyPlannerWidgetRow> spending = new ArrayList<>();

    private double selfEarningsCurrent;
    private double selfEarningsNext;
    private double familyEarningsCurrent;
    private double familyEarningsNext;

    private double selfSpendingCurrent;
    private double selfSpendingNext;
    private double familySpendingCurrent;
    private double familySpendingNext;

    private double selfNetCurrent;
    private double selfNetNext;
    private double familyNetCurrent;
    private double familyNetNext;
}
