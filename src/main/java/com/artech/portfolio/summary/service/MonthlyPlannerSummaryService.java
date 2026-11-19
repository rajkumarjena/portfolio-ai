package com.artech.portfolio.summary.service;

import com.artech.portfolio.summary.dto.MonthlyPlannerWidgetView;

/**
 * MP_V4: the Monthly Planner Summary widget on the dashboard. It follows the
 * same Self/Family model as the Bond and Mutual Fund summary cards.
 */
public interface MonthlyPlannerSummaryService {

    /**
     * Current month and next month for Self and Family, with one aggregated
     * Miscellaneous row per section and only positive amounts included.
     */
    MonthlyPlannerWidgetView getMonthlyPlannerWidget();
}
