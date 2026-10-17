package com.artech.portfolio.bond.repository;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CashFlowDTO {
    private Long id;
    private double amount;
    private LocalDate date;
    private double payout;
    private String schedule;
    private double valuePct;
    private int year;
    private Long bondId;
    private String bondName;
    // BONDBP B.1/B.2: issuer id carried to the report; the page maps it to the
    // issuer name through the cached application param list - no DB lookup.
    private String issuerId;
    // BONDBP_V1: client name for distinguishing whose bonds (esp. for ADMIN/SUPERVISOR)
    private String clientName;
}
