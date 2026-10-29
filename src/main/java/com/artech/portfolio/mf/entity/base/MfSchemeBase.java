package com.artech.portfolio.mf.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One scheme holding of a folio (MF.md 7c): the scheme's own identity plus the
 * opening / closing unit balance the statement prints around its ledger.
 *
 * <p>Mapped onto both the permanent and the temporary table set (MF.md 7/9).</p>
 */
@Data
@MappedSuperclass
public abstract class MfSchemeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "folio_id", nullable = false)
    private Long folioId;

    /** Report source that last refreshed this snapshot row. */
    @Column(name = "refresh_from", length = 30)
    private String refreshFrom;

    @Column(name = "owner_pan", nullable = false, length = 20)
    private String ownerPan;

    @Column(name = "scheme", length = 200)
    private String scheme;

    @Column(name = "advisor", length = 120)
    private String advisor;

    /** RTA code printed in front of the scheme name ("128-", "HDFC-"). */
    @Column(name = "rta_code", length = 20)
    private String rtaCode;

    @Column(name = "rta", length = 40)
    private String rta;

    /** Not printed on the statement - kept for the reference report shape. */
    @Column(name = "fund_type", length = 20)
    private String fundType;

    @Column(name = "isin", length = 30)
    private String isin;

    @Column(name = "amfi", length = 20)
    private String amfi;

    /** "Opening Unit Balance" printed above the ledger. */
    @Column(name = "open_units", precision = 20, scale = 6)
    private BigDecimal openUnits;

    /** "Closing Unit Balance" printed below the ledger. */
    @Column(name = "close_units", precision = 20, scale = 6)
    private BigDecimal closeUnits;

    /** opening + every printed unit movement - checked against close_units. */
    @Column(name = "close_calculated", precision = 20, scale = 6)
    private BigDecimal closeCalculated;
}
