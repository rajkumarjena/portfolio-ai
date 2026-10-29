package com.artech.portfolio.mf.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One uploaded KFINTECH report - the "Previous imports" history (MF.md 17) and
 * the anchor of a reconciliation run.
 *
 * <p>The parsed report is kept as the JSON the pdf converts to (MF.md 4/5), so
 * the upload screen can hand it back for download and every record can be
 * checked at a high level before the reconciliation report is read
 * (MF.md 16).</p>
 */
@Entity
@Data
public class MfImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Login of the client the statement belongs to. */
    private String pan;

    @Column(name = "file_name")
    private String fileName;

    /** KFINTECH | CAMS | CDSL | NSDL - the report's own type. */
    @Column(name = "file_type")
    private String fileType;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt = LocalDateTime.now();

    /** UPLOADED | ACCEPTED | DISCARDED. */
    private String status = "UPLOADED";

    /** Statement period printed in the page header; summary batches store only reportTo. */
    @Column(name = "report_from")
    private LocalDate reportFrom;

    @Column(name = "statement_kind", length = 20)
    private String statementKind = "DETAIL";

    @Column(name = "report_to")
    private LocalDate reportTo;

    /** Investor name printed on the statement - checked against the login. */
    @Column(name = "client_name")
    private String clientName;

    /**
     * The parsed statement as JSON (MF.md 16). A statement can carry ~2500
     * ledger rows, so the column is wide; SQLite stores it as plain text.
     */
    @Column(name = "parsed_json", length = 4_000_000)
    // written by the service, never sent back with the history list (MF.md 16/17)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String parsedJson;

    // counters of the parsed report, shown with the import history
    @Column(name = "folio_count")
    private int folioCount;

    @Column(name = "scheme_count")
    private int schemeCount;

    @Column(name = "txn_count")
    private int txnCount;

    /** Rows the first import added so the ledger reaches the summary (MF.md 10). */
    @Column(name = "adjusted_count")
    private int adjustedCount;

    /** PENDING | GREEN | AMBER | RED - outcome of the last reconciliation. */
    @Column(name = "valid_status")
    private String validStatus = "PENDING";

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
}
