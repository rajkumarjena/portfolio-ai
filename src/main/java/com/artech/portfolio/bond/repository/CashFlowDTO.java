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
}
