package com.artech.portfolio.creditcard.entity;

import com.artech.portfolio.client.entity.Client;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

/**
 * MPC_V1 - one of the client's credit cards: what it is called and the day of
 * the month its billing cycle ends. The card itself is only a definition - the
 * money is in its bills ({@link CreditCardBill}) and purchases
 * ({@link CreditCardPurchase}), which the Monthly Planner reads per cycle.
 */
@Entity
@Table(name = "credit_card",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_credit_card_client_name",
                columnNames = {"client_id", "name"}))
@Data
public class CreditCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // CONSISTENCY.md 6: the card belongs to the signed-in client - bound from
    // the session on save, never from the request body.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Client client;

    /** "HDFC Millennia", "ICICI Amazon Pay", ... - unique per client. */
    private String name;

    /** The day of the month each billing cycle ends on (1..31, clamped to the month). */
    private int billingCycleDay;

    private String note;
}
