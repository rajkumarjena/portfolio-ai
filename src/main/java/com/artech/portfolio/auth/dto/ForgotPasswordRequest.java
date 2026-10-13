package com.artech.portfolio.auth.dto;

import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String pan;
    private String email;
}
