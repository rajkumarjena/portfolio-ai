package com.artech.portfolio.bondsip.dto;

import lombok.Data;

/**
 * BONDBP_V7 #5/#6 update payload for a Bond SIP Transaction: the user may
 * enter the Actual SIP Amount and associate a Bond Name (dropdown id). The
 * other fields are never accepted from the client.
 */
@Data
public class UpdateBondSipTransactionRequest {
    private Double actualSipAmount;
    private Long bondId;
}
