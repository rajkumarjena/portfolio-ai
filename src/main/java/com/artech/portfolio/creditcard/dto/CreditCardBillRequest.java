package com.artech.portfolio.creditcard.dto;

import lombok.Data;

/** MPC_V1 - the bill amount the client enters for one billing cycle. */
@Data
public class CreditCardBillRequest {
    /** Last day of the cycle ("yyyy-MM-dd"); the server snaps it to the card's cycle. */
    private String cycleEnd;
    private Double amount;
    private String note;
}
