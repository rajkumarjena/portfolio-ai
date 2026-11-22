package com.artech.portfolio.creditcard.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * MPC_V1 - one purchase made on a card. The purchase date decides which billing
 * cycle it belongs to, so a purchase on 20 October lands in the cycle that ends
 * on 5 November.
 *
 * <p>MP_V5 #3: the month the transaction is billed in. It defaults to the month
 * of the cycle end plus the card's grace period, and the client can move it
 * between the current and the next month on the Credit Card Bills Entry
 * screen.</p>
 */
@Entity
@Table(name = "credit_card_purchase")
@Data
public class CreditCardPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private CreditCard card;

    private LocalDate purchaseDate;

    private double amount;

    private String description;

    /**
     * MP_V5 #3: the first day of the month this transaction is billed in.
     * Null on rows captured before MP_V5 - the service then derives it from the
     * cycle end plus the grace period.
     */
    private LocalDate billMonth;
}
