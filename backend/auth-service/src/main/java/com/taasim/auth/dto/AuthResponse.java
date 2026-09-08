package com.taasim.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response with signed JWT")
public class AuthResponse {

    @Schema(description = "Signed JWT Bearer access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "Assigned user role", example = "DRIVER")
    private String role;

    @Schema(description = "User email address", example = "soufiane@test.com")
    private String email;

    @Schema(description = "Full name of the user", example = "Soufiane B")
    private String fullName;

    public AuthResponse() {}

    public AuthResponse(String accessToken, String role, String email, String fullName) {
        this.accessToken = accessToken;
        this.role = role;
        this.email = email;
        this.fullName = fullName;
    }

    public String getAccessToken() { return accessToken; }
    public String getRole() { return role; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
}
