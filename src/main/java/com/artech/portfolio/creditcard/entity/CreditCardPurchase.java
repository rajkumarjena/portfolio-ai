package com.artech.portfolio.creditcard.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * MPC_V1 - one purchase made on a card. The purchase date decides which billing
 * cycle it belongs to, so a purchase on 20 October lands in the cycle that ends
 * on 5 November and is planned in November.
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
}
