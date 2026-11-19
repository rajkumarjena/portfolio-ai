package com.artech.portfolio.mfsip.dto;

import lombok.Data;

/** Pause / resume / cancel payload of the SIP list (MF.md 12). */
@Data
public class MfSipStatusRequest {
    private String status;
}
