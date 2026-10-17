package com.artech.portfolio.mf.entity;

import com.artech.portfolio.client.entity.Client;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mutual fund parent record (MF.md 9): one row per client PAN + scheme + folio.
 * Holds SIP registration details (MF.md 2) and the current holding snapshot
 * (units / invested / current) that the portfolio view and reconciliation
 * maintain. Child rows live in MfTransaction.
 *
 * Data-reducing design (MF.md 13): AMC name, category and scheme name are not
 * repeated here - they are resolved through the shared MfScheme master.
 */
@Entity
@Data
public class MfHeader {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Client PAN this record belongs to - the logged-in user (MF.md 4). */
    private String pan;

    /**
     * Owning client (CONSISTENCY.md 6): the mandatory header link that lets the
     * portfolio be tracked per user across investment types. Kept in sync with
     * {@link #pan} by the services; pan stays the dedupe/business key used by
     * the CAS import and repository queries.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Client client;

    /** Owner client id, read-only convenience for reports. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getClientId() {
        return client == null ? null : client.getId();
    }

    @ManyToOne(optional = false)
    @JoinColumn(name = "scheme_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MfScheme scheme;

    private String folioNo;

    /** SIP | ONE_TIME (MF.md 12). */
    private String registrationType;

    /** WEEKLY | MONTHLY | QUARTERLY | HALF_YEARLY | YEARLY (MF.md 6). */
    private String sipType;

    private double sipAmount;

    /** Day the SIP triggers: 1-31 for monthly-and-above, 1-7 (Mon=1) for weekly. */
    private int sipDate;

    private LocalDate sipStartDate;
    private LocalDate sipEndDate;

    private String bankName;
    private String bankAccountNumber;

    /** ACTIVE | PAUSED | CANCELLED | CLOSED (MF.md 10 / 11). */
    private String status = "ACTIVE";

    /** Last date an instalment was generated - scheduler bookkeeping (MF.md 7). */
    private LocalDate lastExecutedDate;

    // Holding snapshot maintained by SIP execution, redemption and import
    private double units;
    private double investedValue;
    private double currentValue;
    private double nav;

    private String notes;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Derived, never stored (MF.md 13): current value - invested value. */
    @Transient
    public double getReturns() {
        return Math.round((currentValue - investedValue) * 100.0) / 100.0;
    }
}
