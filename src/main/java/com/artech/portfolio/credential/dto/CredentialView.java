package com.artech.portfolio.credential.dto;

import java.time.LocalDateTime;

/**
 * CRED_V1 listing row: everything except the password. The screen always renders
 * "********" itself, so a listing response can never leak a credential.
 */
public record CredentialView(Long id, String clientPan, String appName, String username,
                             String website, String notes,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
}
