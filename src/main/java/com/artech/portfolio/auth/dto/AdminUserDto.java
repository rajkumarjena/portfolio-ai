package com.artech.portfolio.auth.dto;

import lombok.Data;

import java.util.List;

/**
 * Safe projection for the admin user-management screen.
 * Never exposes password hashes or reset tokens.
 *
 * BONDBP_V10 #4: accountStatus is the client's operational status
 * (ACTIVE / HOLD / CANCELLED) - distinct from `locked`, which only means
 * too many failed logins.
 */
@Data
public class AdminUserDto {
    private Long id;
    private String name;
    private String pan;
    private String role;
    private List<String> emails;
    private boolean locked;
    private int failedAttempts;
    private String accountStatus;
}
