package com.artech.portfolio.mfsip.dto;

import lombok.Data;

import java.time.LocalDate;

/** The Mutual Fund SIP / Investment Entry screen's payload (MF.md 1-6, 11, 12). */
@Data
public class MfSipRequest {
    private MfSipSchemeDto scheme;
    private String folioNo;
    private String registrationType;
    private String sipType;
    private Double sipAmount;
    private Integer sipDate;
    private LocalDate sipStartDate;
    private LocalDate sipEndDate;
    private String bankName;
    private String bankAccountNumber;
    private String status;
    private String notes;
    private Double nav;
}
