package com.artech.portfolio.credential.dto;

/** CRED_V1 "Create Vault": Master Password and its confirmation. */
public record VaultSetupRequest(String masterPassword, String confirmPassword) {
}
