package com.artech.portfolio.fd.entity;

import com.artech.portfolio.client.entity.Client;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Data
public class FixedDeposit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // CONSISTENCY.md 6: every investment row belongs to a client (the logged-in
    // user). The relation is bound server-side from the session on save - the
    // API never accepts an owner from the request body. The column stays NULL-
    // tolerant only because SQLite cannot ALTER an existing table to NOT NULL;
    // ownership is enforced by the service (requireClient) and StartupClient-
    // Backfill keeps every legacy row assigned.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Client client;

    /** Owner PAN, read-only convenience for the UI / reports (never accepted on write). */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getClientPan() {
        return client == null ? null : client.getPan();
    }

    private String bankName;
    private double depositAmount;
    private int tenureMonths;           // Tenure period in months
    private LocalDate depositDate;
    private LocalDate maturityDate;
    private double interestRate;        // Interest %
    private double maturityAmount;

    private String status;              // ACTIVE or CLOSED (premature closure)
    private LocalDate closureDate;      // Set when closed before maturity
    private double closureAmount;       // Amount paid out on premature closure
    private String closureReason;
    private String notes;
}
