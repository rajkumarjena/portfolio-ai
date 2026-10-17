package com.artech.portfolio.bond.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;

@Entity
@Data
// ADDED: Table configuration defining the index on the 'date' column
@Table(
        name = "bond_cash_flow",
        indexes = {
                @Index(name = "idx_bond_cashflow_date", columnList = "date")
        }
)
public class BondCashFlow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int year;                 // Year
    private String schedule;          // Payment Schedule (e.g., "Payout #1")
    private LocalDate date;           // Payment Date (String to match your date format)
    private double payout;            // Interest Payout
    private double amount;            // Redemption Amount
    private double valuePct;          // Redemption Value %

    @ManyToOne
    @JoinColumn(
            name = "bond_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
            // Stops Hibernate from  executing ALTER TABLE
    )
    @JsonBackReference
    @ToString.Exclude
    private Bond bond;
}
