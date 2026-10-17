package com.artech.portfolio.auth.dto;

/**
 * Read-only snapshot of the signed-in user that every page template renders in
 * the top-right corner (CONSISTENCY.md 5). Populated server-side by
 * CurrentUserInterceptor - the pages never fetch it themselves.
 */
public record CurrentUserView(String name, String pan, String role, boolean admin) {

    /** "Sonalika Mohapatra (USER)" - single line for the header chip. */
    public String getDisplayName() {
        String who = (name == null || name.isBlank()) ? pan : name;
        return who + " (" + role + ")";
    }
}
