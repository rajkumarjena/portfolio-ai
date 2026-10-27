package com.artech.portfolio.mf.entity;

import com.artech.portfolio.mf.entity.base.MfPortfolioSummaryBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Accepted PORTFOLIO SUMMARY of the last reconciled KFINTECH statement
 * (MF.md 7a/13). {@link com.artech.portfolio.mf.entity.tmp.MfTmpPortfolioSummary}
 * is its temporary twin holding the report currently under review.
 */
@Entity
@Table(name = "mf_portfolio_summary")
public class MfPortfolioSummary extends MfPortfolioSummaryBase {
}
