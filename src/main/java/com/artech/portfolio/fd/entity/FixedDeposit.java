package com.artech.portfolio.fd.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Data
public class FixedDeposit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String bankName;
    private double depositAmount;
    private int tenureMonths;           // Tenure period in months
    private LocalDate depositDate;
    private LocalDate maturityDate;
    private double interestRate;        // Interest %
    private double maturityAmount;

    private String status;              // ACTIVE or CLOSED (premature closure)
    private LocalDate closureDate;      // Set when closed before maturity
    private double closureAmount;       // Amount paid out on premature closure
    private String closureReason;
    private String notes;
}
