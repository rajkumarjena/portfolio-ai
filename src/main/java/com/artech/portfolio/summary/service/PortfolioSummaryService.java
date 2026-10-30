package com.artech.portfolio.summary.service;

import com.artech.portfolio.summary.dto.BondWidgetView;
import com.artech.portfolio.summary.dto.MfWidgetView;

/**
 * BONDBP_V6: Portfolio Summary widgets shown top-left of the dashboard.
 * One method per widget so each investment type can be developed on its own
 * (#1: Bonds now, FDs / Shares / Mutual Funds later).
 */
public interface PortfolioSummaryService {

    /** BONDBP_V6 #4/#6: the Bonds widget - Self and Family figures computed entirely server-side. */
    BondWidgetView getBondWidget();

    /**
     * MFR_V11: the Mutual Funds widget that sits next to the Bonds card -
     * Investment and Market Value for Self and Family over the sample periods,
     * with the corrected calculation of MFR_V12 #2-#7 (ADJUSTED rows excluded,
     * the period's units at each scheme's NAV, the printed total, the red-font
     * flag), again computed entirely server-side.
     */
    MfWidgetView getMfWidget();
}
