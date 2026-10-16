package com.artech.portfolio.mf.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDate;

/**
 * One parsed row of an uploaded CAS report (MF.md 14) held for reconciliation.
 * Nothing here touches MfHeader / MfTransaction until the user accepts the
 * batch (MF.md 16). Each line carries its status so the screen can paint the
 * standard color codes and support filtering of mismatches / unregistered
 * schemes (MF.md 15 / 20).
 */
@Entity
@Data
public class MfImportLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id")
    private Long batchId;

    private int lineNo;

    /** HOLDING (report header sheet) | TRANSACTION (detail sheet). */
    private String lineType;

    private String schemeName;
    private String amcName;
    private String category;
    private String folioNo;

    private LocalDate txnDate;
    private String txnType;
    private String description;

    private double units;
    private double nav;
    private double amount;

    // Holding-only values
    private double investedValue;
    private double currentValue;
    private double returns;

    /**
     * NEW | EXISTS | NEW_SCHEME | MAPPED | ERROR - see MfConstants.
     * matchedHeaderId: existing MfHeader this line will merge into (dedupe).
     * targetSchemeId: scheme the user remapped this line to (MF.md 20).
     */
    private String status;

    @Column(name = "matched_header_id")
    private Long matchedHeaderId;

    @Column(name = "target_scheme_id")
    private Long targetSchemeId;

    private String message;

    /** Fingerprint kept at parse time for the no-duplicates check (MF.md 22). */
    @Column(name = "dedupe_key")
    private String dedupeKey;
}
