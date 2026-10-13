package com.artech.portfolio.auth.dto;

import lombok.Data;

import java.util.List;

/**
 * Safe projection for the admin user-management screen.
 * Never exposes password hashes or reset tokens.
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
}
