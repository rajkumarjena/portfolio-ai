package com.artech.portfolio.summary.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MFR_V11 #2/#3: one row of the Mutual Fund portfolio-summary widget, exactly
 * as the sample sheet lays it out - a captioned period on the left and then
 * FOUR figures: Investment and Market Value for Self, and the same two for
 * Family.
 *
 * <p>MFR_V12 #2-#7 corrects what the two figures are:</p>
 * <ul>
 *   <li><b>Investment</b> - the sum of the {@code amount} column of
 *       {@code mf_folio_scheme_transactions} inside the period, ignoring the
 *       ADJUSTED rows of a first import (#7), so a redemption or a switch-out
 *       inside the period nets against the purchases of the same period.</li>
 *   <li><b>Market Value</b> - for every scheme transacted during the period,
 *       the units moved <i>in that period</i> times the scheme's NAV from
 *       {@code mf_folio_schemes_valuation}, then all of them added up. Each
 *       scheme is priced with its own NAV before the values are summed.</li>
 *   <li><b>The last row</b> is not a period at all: it is the statement's own
 *       PORTFOLIO SUMMARY total ({@code mf_portfolio_summary.is_total = 1},
 *       #5), where cost value is the investment and market value is the same
 *       market value the statement prints.</li>
 * </ul>
 *
 * <p>Money is {@link BigDecimal} because it is summed from currency columns -
 * the widget renders it verbatim, it never re-does the arithmetic.</p>
 */
@Data
public class MfWidgetRow {

    /** e.g. "Current Month Investment" - the sample's left column. */
    private String label;

    /** MONTH | QUARTER | FY | PREV_FY | TOTAL - machine hint for future consumers. */
    private String period;

    /**
     * Period the row covers (both ends inclusive). The Total row is a
     * point-in-time figure and carries no range, so both are null there.
     */
    private LocalDate from;
    private LocalDate to;

    /** Capital invested in the period by the signed-in user. */
    private BigDecimal selfInvestment;
    /** Current worth of that period's schemes, at each scheme's latest NAV. */
    private BigDecimal selfMarketValue;

    /** Same two figures over the user plus every associated family member. */
    private BigDecimal familyInvestment;
    private BigDecimal familyMarketValue;

    /**
     * MFR_V12 #6: true when the market value is worth less than the money put
     * in - the page paints exactly these cells red. Decided here so the front
     * end never does arithmetic of its own.
     */
    private boolean selfInLoss;
    private boolean familyInLoss;
}
