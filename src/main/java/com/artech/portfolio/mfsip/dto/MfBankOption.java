package com.artech.portfolio.mfsip.dto;

import lombok.Data;

/** One bank account of the client, for the SIP entry screen's drop-down (MF.md 3). */
@Data
public class MfBankOption {
    private String bankName;
    private String accountNumber;
}
