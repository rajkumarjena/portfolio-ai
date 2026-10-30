package com.artech.portfolio.mf.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A staged summary-statement holding shown for review before it is accepted. */
@Entity
@Table(name = "mf_summary_reconciliation")
@Data
public class MfSummaryReconciliationLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "amc_id")
    private Long amcId;

    /** Existing accepted folio resolved by exact or provisional folio matching. */
    @Column(name = "resolved_folio_id")
    private Long resolvedFolioId;

    /** True when the report folio only partially matched a stored identifier. */
    @Column(name = "probable_folio_match")
    private boolean probableFolioMatch;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.JoinColumn(name = "amc_id", insertable = false, updatable = false,
            foreignKey = @jakarta.persistence.ForeignKey(jakarta.persistence.ConstraintMode.NO_CONSTRAINT))
    private MfAmc amcReference;

    @Column(name = "owner_pan", nullable = false, length = 20)
    private String ownerPan;

    @Column(name = "folio_no", nullable = false, length = 40)
    private String folioNo;

    /** Display/edit value only; the normalized AMC is stored through amc_id. */
    @jakarta.persistence.Transient
    private String amc;

    public String getAmc() {
        return amc != null ? amc : (amcReference == null ? null : amcReference.getName());
    }

    public void setAmc(String amc) {
        this.amc = amc;
    }

    @Override
    public String toString() {
        return "MfSummaryReconciliationLine{" + "id=" + id + ", batchId=" + batchId
                + ", amcId=" + amcId + ", folioNo='" + folioNo + '\''
                + ", scheme='" + scheme + '\'' + '}';
    }

    @Column(name = "scheme", length = 200)
    private String scheme;

    @Column(name = "isin", length = 30)
    private String isin;

    @Column(name = "cost_value", precision = 20, scale = 4)
    private BigDecimal costValue;

    @Column(name = "units", precision = 20, scale = 6)
    private BigDecimal units;

    @Column(name = "nav_date")
    private LocalDate navDate;

    @Column(name = "price", precision = 20, scale = 6)
    private BigDecimal price;

    @Column(name = "market_value", precision = 20, scale = 4)
    private BigDecimal marketValue;

    @Column(name = "registrar", length = 30)
    private String registrar;

    @Column(name = "match_status", length = 20)
    private String matchStatus;

    @Column(name = "message", length = 500)
    private String message;
}
