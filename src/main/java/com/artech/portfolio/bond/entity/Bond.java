package com.artech.portfolio.bond.entity;

import com.artech.portfolio.common.entity.AppParamConfig;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class Bond {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String bondName;
    private String issuer;
    private String creditRating;
    private LocalDate investmentDate;
    private double investmentAmountPerUnit;
    private double faceValuePerUnit;
    private int units;
    private double couponRate;
    private String couponType;
    private String couponFrequency;
    private LocalDate interestPaymentStartDate;
    private LocalDate maturityDate;
    private String typeOfSecurity;
    private String repaymentPriority;
    private String staggeredBond;
    private String listing;
    private String notes;

    // Relational mapping link to child table structure
    @OneToMany(mappedBy = "bond", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference // Properly serializes the child list to your front-end API requests
    private List<CashFlowData> cashFlowData = new ArrayList<>();

//    // Relational mapping link to child table structure
//    @OneToMany(mappedBy = "bond", cascade = CascadeType.ALL, orphanRemoval = true)
//    @JsonManagedReference // Properly serializes the child list to your front-end API requests
//    private List<AppParamConfig> bondConfigData = new ArrayList<>();

    /**
     * Helper utility method to safely attach cash flow items
     * while maintaining bidirectional entity synchronisation integrity.
     */
    public void addCashFlow(CashFlowData cashFlow) {
        cashFlowData.add(cashFlow);
        cashFlow.setBond(this);
    }
}
