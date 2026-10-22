package com.artech.portfolio.client.util;

public class ClientConstants {
    // Indian PAN format: 5 letters, 4 digits, 1 letter - the business key that identifies a client
    public static final String PAN_REGEX = "[A-Za-z]{5}[0-9]{4}[A-Za-z]";

    // AUTH.md (d): every registered user gets USER by default; ADMIN can elevate to SUPERVISOR/ADMIN
    public static final String ROLE_USER = "USER";
    public static final String ROLE_SUPERVISOR = "SUPERVISOR";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final String[] ALL_ROLES = {ROLE_USER, ROLE_SUPERVISOR, ROLE_ADMIN};

    // BONDBP_V10 #4: client account status
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_HOLD = "HOLD";
    public static final String STATUS_CANCELLED = "CANCELLED";
}
