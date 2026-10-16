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
 * One uploaded MF Central / CAMS CAS statement (MF.md 14) waiting in the
 * reconciliation screen. Stores the report's own header totals so the validate
 * step can compare them with the parsed detail rows (MF.md 15) before anything
 * is written to MfHeader / MfTransaction (MF.md 16).
 */
@Entity
@Data
public class MfImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String pan;

    @Column(name = "file_name")
    private String fileName;

    /** XLSX | CSV | PDF. */
    private String fileType;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt = LocalDateTime.now();

    /** UPLOADED | ACCEPTED | REJECTED. */
    private String status = "UPLOADED";

    private LocalDate reportFrom;
    private LocalDate reportTo;

    /** Identity printed on the statement - checked against the logged-in PAN. */
    private String clientName;
    private String reportPan;

    // Totals printed on the report itself (row 9 of the xlsx header sheet)
    private double rptInvested;
    private double rptCurrent;
    private double rptProfit;
    private double rptUnits;
    private int rptHoldingsCount;
    private int rptTxnCount;

    /** PENDING | GREEN | AMBER | RED - reconciliation color code (MF.md 15). */
    @Column(name = "valid_status")
    private String validStatus = "PENDING";

    /** JSON array of individual check results shown on the reconciliation page. */
    @Column(name = "validation_details", length = 8000)
    private String validationDetails;

    // Counters refreshed on every validate (used by the summary cards)
    private int newCount;
    private int existsCount;
    private int newSchemeCount;
    private int mappedCount;
    private int errorCount;
    private int holdingCount;
    private int txnLineCount;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
}
