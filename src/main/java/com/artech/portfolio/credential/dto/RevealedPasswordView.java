package com.artech.portfolio.credential.dto;

/**
 * CRED_V1 Reveal / Copy: the decrypted password of ONE credential, returned only
 * after the Master Password was verified again for that request. The browser
 * hides it again after 30 seconds and never stores it.
 */
public record RevealedPasswordView(String password) {
}
