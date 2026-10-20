package com.artech.portfolio.bondsip.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * BONDBP_V7 #2 create payload: only Issuer, SIP Date and SIP Amount are
 * meaningful - Create Date is auto-populated server-side and Status is always
 * CREATED (#3), both disabled in the form.
 */
@Data
public class CreateBondSipRequest {
    private String issuer;
    private LocalDate sipDate;
    private Double sipAmount;

    /** Mandatory: WEEKLY, MONTHLY, QUARTERLY, HALF-YEARLY or YEARLY. */
    private String frequency;
}
