package com.artech.portfolio.creditcard.dto;

import lombok.Data;

/** MPC_V1 - add or edit a card: its name and the day its billing cycle ends. */
@Data
public class CreditCardRequest {
    /** Null when the card is being added. */
    private Long id;
    private String name;
    /** 1..31 - the day of the month the billing cycle ends on. */
    private Integer billingCycleDay;
    private String note;
}
