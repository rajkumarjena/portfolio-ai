package com.artech.portfolio.mfsip.entity;

import com.artech.portfolio.client.entity.Client;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * MF.md 1/7/11 - one Mutual Fund registration of a client: a running SIP or a
 * one time purchase, with the scheme, folio, bank account, period, amount,
 * validity window and the holding it has built up so far.
 *
 * <p>The daily 03:00 execution (and the "Run Due Instalments" button) add one
 * instalment per due date of the SIP period and stamp
 * {@code lastExecutedOn}, which makes the execution idempotent.</p>
 */
@Entity
@Table(name = "mf_sip_registration")
@Data
public class MfSipRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // CONSISTENCY.md 6: the registration belongs to the signed-in client -
    // bound from the session on save, never from the request body.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Client client;

    private String schemeName;
    private String amcName;
    private String category;
    private String folioNo;

    /** SIP (auto-executed periodically) or ONE_TIME. */
    private String registrationType;

    /** WEEKLY / MONTHLY / QUARTERLY / HALF_YEARLY / YEARLY. */
    private String sipType;
    private double sipAmount;

    /** Day of the month the SIP triggers (1-31). */
    private int sipDay;
    private LocalDate sipStartDate;
    private LocalDate sipEndDate;

    private String bankName;
    private String bankAccountNumber;

    /** ACTIVE / PAUSED / CANCELLED / CLOSED. */
    private String status;
    private String notes;

    /** Last known NAV - drives units and the current value. */
    private double nav;
    private double units;
    private double investedValue;
    private double currentValue;

    /** The latest instalment date already executed (idempotency anchor). */
    private LocalDate lastExecutedOn;
}
