package com.artech.portfolio.creditcard.dto;

import lombok.Data;

/** MPC_V1 - one purchase: its date decides the billing cycle it is billed in. */
@Data
public class CreditCardPurchaseRequest {
    /** "yyyy-MM-dd" - blank means today. */
    private String purchaseDate;
    private Double amount;
    private String description;
}
