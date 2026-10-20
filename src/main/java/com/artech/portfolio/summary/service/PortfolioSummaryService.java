package com.artech.portfolio.summary.service;

import com.artech.portfolio.summary.dto.BondWidgetView;

/**
 * BONDBP_V6: Portfolio Summary widgets shown top-left of the dashboard.
 * One method per widget so each investment type can be developed on its own
 * (#1: Bonds now, FDs / Shares / Mutual Funds later).
 */
public interface PortfolioSummaryService {

    /** BONDBP_V6 #4/#6: the Bonds widget - Self and Family figures computed entirely server-side. */
    BondWidgetView getBondWidget();
}
