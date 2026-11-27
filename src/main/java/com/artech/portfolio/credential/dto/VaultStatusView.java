package com.artech.portfolio.credential.dto;

/**
 * CRED_V1 - what the screen needs to decide which panel to show: whether a
 * vault exists at all and whether its DEK is currently in memory. Never carries
 * a key, a password or the salt.
 */
public record VaultStatusView(boolean initialized, boolean unlocked, int idleLockMinutes) {
}
