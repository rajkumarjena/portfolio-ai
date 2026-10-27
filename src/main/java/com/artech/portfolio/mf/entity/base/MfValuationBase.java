package com.artech.portfolio.mf.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The latest valuation printed under a scheme's closing balance (MF.md 7e):
 * NAV date, NAV, total cost value and market value - the current worth of the
 * holding the statement reports (MF.md 11).
 *
 * <p>Mapped onto both the permanent and the temporary table set (MF.md 7/9).</p>
 */
@Data
@MappedSuperclass
public abstract class MfValuationBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_id", nullable = false)
    private Long schemeId;

    @Column(name = "owner_pan", nullable = false, length = 20)
    private String ownerPan;

    /** "NAV on 23-OCT-2026" / "Market Value on 23-OCT-2026". */
    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "nav", precision = 20, scale = 6)
    private BigDecimal nav;

    @Column(name = "cost_value", precision = 20, scale = 4)
    private BigDecimal costValue;

    @Column(name = "market_value", precision = 20, scale = 4)
    private BigDecimal marketValue;
}
