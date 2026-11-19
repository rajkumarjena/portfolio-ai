package com.artech.portfolio.creditcard.dto;

import lombok.Data;

import java.time.LocalDate;

/** MPC_V1 - one purchase as the popup lists it. */
@Data
public class CreditCardPurchaseView {
    private Long id;
    private LocalDate purchaseDate;
    private String description;
    private double amount;
}
