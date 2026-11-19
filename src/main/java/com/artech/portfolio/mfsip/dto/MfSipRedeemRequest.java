package com.artech.portfolio.mfsip.dto;

import lombok.Data;

/** Redeem dialog payload: units, an amount to sell, or the complete sell-off (MF.md 10). */
@Data
public class MfSipRedeemRequest {
    private String mode;
    private double value;
}
