package com.artech.portfolio.credential.dto;

/**
 * CRED_V1 "Add Credential" / "Edit Credential". On an edit {@code password} may be
 * left blank to keep the stored one - the screen never sends a password it did
 * not just encrypt itself.
 *
 * <p>{@code clientPan} is kept for the shape of the screen, but it is ignored:
 * the stored PAN is always the signed-in client's own.</p>
 */
public record CredentialRequest(String clientPan, String appName, String username,
                                String password, String website, String notes) {
}
