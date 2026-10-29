package com.artech.portfolio.mf.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Columns of a PORTFOLIO SUMMARY line of the KFINTECH report - the AMC level
 * cost / market value the statement prints on page 1 (MF.md 8/11).
 *
 * <p>The same columns are mapped twice (MF.md 7/9): onto the permanent table
 * that keeps the last accepted statement and onto the temporary table that
 * holds the report currently under reconciliation. Only the mapping is shared;
 * the two tables stay independent so a rejected upload can never touch the
 * accepted data.</p>
 */
@Data
@MappedSuperclass
public abstract class MfPortfolioSummaryBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owner of the statement - every row of both table sets is scoped by it. */
    @Column(name = "owner_pan", nullable = false, length = 20)
    private String ownerPan;

    @Column(name = "amc_id", nullable = true)
    private Long amcId;

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "amc_id", insertable = false, updatable = false,
            foreignKey = @jakarta.persistence.ForeignKey(jakarta.persistence.ConstraintMode.NO_CONSTRAINT))
    private com.artech.portfolio.mf.entity.MfAmc amcReference;

    /** Display-only compatibility label; the database stores only amc_id. */
    @Transient
    private String amc;

    public String getAmc() {
        return total ? "Total" : (amc != null ? amc
                : (amcReference == null ? null : amcReference.getName()));
    }

    public void setAmc(String amc) {
        this.amc = amc;
    }

    /** Report source that last refreshed this snapshot row. */
    @Column(name = "refresh_from", length = 30)
    private String refreshFrom;

    /** DETAIL_STATEMENT or SUMMARY_STATEMENT. */
    @Column(name = "match_condition", length = 30)
    private String matchCondition = "DETAIL_STATEMENT";

    @Column(name = "cost_value", precision = 20, scale = 4)
    private BigDecimal costValue;

    @Column(name = "market_value", precision = 20, scale = 4)
    private BigDecimal marketValue;

    /** true for the printed "Total" line of the summary block. */
    @Column(name = "is_total")
    private boolean total;
}
