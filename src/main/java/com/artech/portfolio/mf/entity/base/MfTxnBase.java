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
 * One ledger row of a scheme (MF.md 7g) - purchase, SIP instalment, redemption,
 * switch, dividend, stamp duty ... plus the ADJUSTED row the first import adds
 * so the ledger adds up to the statement's summary (MF.md 10).
 *
 * <p>Mapped onto both the permanent and the temporary table set (MF.md 7/9).</p>
 */
@Data
@MappedSuperclass
public abstract class MfTxnBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_id", nullable = false)
    private Long schemeId;

    @Column(name = "owner_pan", nullable = false, length = 20)
    private String ownerPan;

    @Column(name = "txn_date")
    private LocalDate txnDate;

    @Column(name = "description", length = 500)
    private String description;

    /** Signed money: positive for a purchase, negative for a sale. */
    @Column(name = "amount", precision = 20, scale = 4)
    private BigDecimal amount;

    /** Signed units of the movement. */
    @Column(name = "units", precision = 20, scale = 6)
    private BigDecimal units;

    /** NAV / price printed on the row. */
    @Column(name = "nav", precision = 20, scale = 6)
    private BigDecimal nav;

    /** Unit balance printed on the row. */
    @Column(name = "balance", precision = 20, scale = 6)
    private BigDecimal balance;

    /** PURCHASE | PURCHASE_SIP | REDEMPTION | ... | ADJUSTED (MF.md 10). */
    @Column(name = "type", length = 30)
    private String type;

    @Column(name = "dividend_rate", precision = 20, scale = 6)
    private BigDecimal dividendRate;

    @Column(name = "gift_folio", length = 40)
    private String giftFolio;
}
