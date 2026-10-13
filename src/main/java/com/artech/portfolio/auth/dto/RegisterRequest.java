package com.artech.portfolio.auth.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String name;
    private String pan;
    private String password;
    private String email;
    private String mobileNumber; // optional
}
