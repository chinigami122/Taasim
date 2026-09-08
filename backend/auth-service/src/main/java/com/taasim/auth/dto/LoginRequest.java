package com.taasim.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User login credentials payload")
public class LoginRequest {

    @Schema(description = "Registered email address", example = "soufiane@test.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "Account password", example = "secret123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    public LoginRequest() {}

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
