package com.artech.portfolio.bond.entity;

import com.artech.portfolio.client.entity.Client;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class Bond {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // CONSISTENCY.md 6: every investment row belongs to a client (the logged-in
    // user). Bound server-side from the session on save - never from the body.
    // Column stays NULL-tolerant only because SQLite cannot ALTER an existing
    // table to NOT NULL; ownership is enforced by the service and by
    // StartupClientBackfill for legacy rows.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Client client;

    /** Owner PAN, read-only convenience for the UI / reports. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getClientPan() {
        return client == null ? null : client.getPan();
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getClientName() {
        return client == null ? null : client.getName();
    }

    private String bondName;
    private String issuer;
    private String creditRating;
    private LocalDate investmentDate;
    private double investmentAmountPerUnit;
    private double faceValuePerUnit;
    private int units;
    private double couponRate;
    private String couponType;
    private String couponFrequency;
    private LocalDate interestPaymentStartDate;
    private LocalDate maturityDate;
    private String typeOfSecurity;
    private String repaymentPriority;
    private String staggeredBond;
    private String listing;
    private String notes;

    // Relational mapping link to child table structure
    @OneToMany(mappedBy = "bond", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference // Properly serializes the child list to your front-end API requests
    private List<BondCashFlow> cashFlowData = new ArrayList<>();

    /**
     * Helper utility method to safely attach cash flow items
     * while maintaining bidirectional entity synchronisation integrity.
     */
    public void addCashFlow(BondCashFlow cashFlow) {
        cashFlowData.add(cashFlow);
        cashFlow.setBond(this);
    }
}
