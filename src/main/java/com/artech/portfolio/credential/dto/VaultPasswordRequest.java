package com.artech.portfolio.credential.dto;

/** CRED_V1: the Master Password typed on Unlock, Reveal or Copy. */
public record VaultPasswordRequest(String masterPassword) {
}
