package com.artech.portfolio.monthlyplanner.service;

import com.artech.portfolio.monthlyplanner.dto.PlannerHistoryRowView;
import com.artech.portfolio.monthlyplanner.dto.PlannerMonthView;
import com.artech.portfolio.monthlyplanner.dto.SavePlannerRequest;

import java.time.LocalDate;
import java.util.List;

/**
 * MP_V1 - the Monthly Planner process. Everything is scoped to the signed-in
 * client (CONSISTENCY.md 6): earnings and spending are personal data, so no
 * client id ever comes from a request body.
 */
public interface MonthlyPlannerService {

    /** One plan month - explicit figures, carried-over recurring ones and SIP auto-figures. */
    PlannerMonthView month(LocalDate month);

    /**
     * Read-only build of one client's month, used by the dashboard summary for
     * the signed-in client and each associated family member. The client id
     * always comes from the session-derived scope; it is never a request value.
     */
    PlannerMonthView monthForClient(Long clientId, LocalDate month);

    /**
     * MP_V1 #2: the Cash-Flow Report - the selected month plus the configured
     * number of following months, so the client can plan ahead.
     */
    List<PlannerMonthView> cashFlow(LocalDate month);

    /** Save the figures typed on the Earnings or Spending screen. */
    PlannerMonthView save(SavePlannerRequest request);

    /**
     * MP_V1 #8: the figures captured for every month of a window (the last
     * {@code MONTHS_IN_HISTORY_WINDOW} months when none is given), one row per
     * month and category - the data the analytics will be built from.
     */
    List<PlannerHistoryRowView> history(LocalDate from, LocalDate to);
}
