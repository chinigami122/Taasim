package com.taasim.auth.dto;

public class AuthResponse {
    private String accessToken;
    private String role;
    private String email;
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
