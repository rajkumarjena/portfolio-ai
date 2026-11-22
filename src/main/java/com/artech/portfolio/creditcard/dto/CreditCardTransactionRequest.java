package com.artech.portfolio.creditcard.dto;

import lombok.Data;

/** MP_V5 #3.iv: one transaction whose billed month the client changed. */
@Data
public class CreditCardTransactionRequest {
    private Long purchaseId;
    /** The chosen month - "yyyy-MM" (or a full date); current or next month only. */
    private String billMonth;
}
