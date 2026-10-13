package com.artech.portfolio.auth.service;

import com.artech.portfolio.auth.dto.ForgotPasswordRequest;
import com.artech.portfolio.auth.dto.RegisterRequest;
import com.artech.portfolio.auth.dto.ResetPasswordRequest;
import com.artech.portfolio.client.entity.Client;

public interface AuthService {
    /** Register a new client-login; PAN is the identity and gets the USER role by default (AUTH.md d). */
    Client register(RegisterRequest request);

    /** Start the password-reset flow; emails a short-lived token to the client's email (AUTH.md h). */
    void forgotPassword(ForgotPasswordRequest request);

    /** Consume a reset token and store the new BCrypt-hashed password (AUTH.md h). */
    void resetPassword(ResetPasswordRequest request);

    /** Pre-check for the reset page: is this token known and unexpired? */
    boolean isResetTokenValid(String token);
}
