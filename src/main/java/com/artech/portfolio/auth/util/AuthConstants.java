package com.artech.portfolio.auth.util;

public class AuthConstants {
    // Authority prefix Spring Security expects before a role name
    public static final String ROLE_PREFIX = "ROLE_";

    // AUTH.md (g): brute-force protection - lock the account after consecutive failures
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCK_DURATION_MS = 15 * 60 * 1000L; // 15 minutes

    // AUTH.md (h): password reset links are short-lived
    public static final long RESET_TOKEN_TTL_MS = 30 * 60 * 1000L; // 30 minutes

    // AUTH.md (h): password policy - at least 8 characters with letters and digits
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,}$";
    public static final String PASSWORD_POLICY_MESSAGE =
            "Password must be at least 8 characters and contain both letters and digits";
}
