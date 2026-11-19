package com.artech.portfolio.mfsip.dto;

import lombok.Data;

/** What the Redeem dialog reports back: units sold, proceeds and FIFO cost basis. */
@Data
public class MfSipRedeemResult {
    private double unitsSold;
    private double proceeds;
    private double costBasis;
}
