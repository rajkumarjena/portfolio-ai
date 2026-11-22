package com.artech.portfolio.creditcard.dto;

import lombok.Data;

/**
 * MPC_V1 - one purchase: its date decides the billing cycle it belongs to.
 * MP_V5 #3: the month it is billed in is derived on the server from that cycle's
 * end plus the card's grace period.
 */
@Data
public class CreditCardPurchaseRequest {
    /** "yyyy-MM-dd" - blank means today. */
    private String purchaseDate;
    private Double amount;
    private String description;
}
