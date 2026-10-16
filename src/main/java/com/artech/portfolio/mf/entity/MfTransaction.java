package com.artech.portfolio.mf.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mutual fund child transaction (MF.md 8 / 9): one SIP instalment, purchase,
 * redemption or imported CAS entry. HeaderId points at MfHeader as a plain id
 * column (no back-reference) which keeps list JSON light and avoids cycles.
 */
@Entity
@Data
public class MfTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "header_id")
    private Long headerId;

    private String pan;

    private LocalDate txnDate;

    /** SIP_INSTALMENT | PURCHASE | REDEMPTION | SWITCH_IN | ... (see MfConstants). */
    private String txnType;

    private String description;

    /** Signed: positive for a buy, negative for a redemption. */
    private double units;

    /** NAV / price on the transaction date. */
    private double nav;

    /** Signed invested value: positive for a buy, negative for sale proceeds. */
    private double amount;

    /** SIP | MANUAL | IMPORT | REDEMPTION. */
    private String source;

    /**
     * Normalised fingerprint (pan|scheme|folio|date|type|units|amount) used to
     * guarantee a transaction is never imported twice (MF.md 22).
     */
    @Column(name = "dedupe_key")
    private String dedupeKey;

    private LocalDateTime createdAt = LocalDateTime.now();
}
