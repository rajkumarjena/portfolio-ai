package com.artech.portfolio.creditcard.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * MPC_V1 - the bill amount the client enters for one billing cycle of one card.
 *
 * <p>One row per card + cycle end, so the bill is entered once and the planner
 * keeps using it; the cycle's end date is what puts the amount in a month.</p>
 */
@Entity
@Table(name = "credit_card_bill",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_credit_card_bill_card_cycle",
                columnNames = {"card_id", "cycle_end"}))
@Data
public class CreditCardBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private CreditCard card;

    /** Last day of the billing cycle this bill belongs to. */
    private LocalDate cycleEnd;

    /** What the statement says; the planner counts this when it is entered. */
    private double amount;

    private String note;
}
