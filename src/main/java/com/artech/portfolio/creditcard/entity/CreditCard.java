package com.artech.portfolio.creditcard.entity;

import com.artech.portfolio.client.entity.Client;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

/**
 * MPC_V1 - one of the client's credit cards: what it is called, the day of the
 * month its billing cycle ends and, since MP_V5, the grace period the bank gives
 * before the amount is due.
 *
 * <p>MP_V5: the money is in the card's purchases
 * ({@link CreditCardPurchase}); each purchase is billed in a month decided by
 * the end of its billing cycle plus the grace period.</p>
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

    /**
     * MP_V5 #2: days after the cycle end before the amount is due. Nullable so
     * SQLite can add the column to an existing table; null reads as 0.
     */
    private Integer gracePeriodDays;

    private String note;
}
