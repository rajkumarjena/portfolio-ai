package com.artech.portfolio.mf.entity.tmp;

import com.artech.portfolio.mf.entity.base.MfPortfolioSummaryBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Temporary PORTFOLIO SUMMARY of the statement currently being reconciled
 * (MF.md 7a/9). It is only emptied once a new report has been read
 * successfully, so a failed upload always leaves the last good staging data in
 * place (MF.md 9).
 */
@Entity
@Table(name = "mf_tmp_portfolio_summary")
public class MfTmpPortfolioSummary extends MfPortfolioSummaryBase {
}
