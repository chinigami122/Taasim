package com.taasim.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User registration payload")
public class RegisterRequest {

    @Schema(description = "User email address", example = "soufiane@test.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "Account password (plain text, will be hashed with BCrypt)", example = "secret123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "Full name of the user", example = "Soufiane B")
    private String fullName;

    @Schema(description = "Contact phone number", example = "+212600000000")
    private String phone;

    @Schema(description = "User role in the platform", example = "DRIVER", allowableValues = {"CLIENT", "DRIVER", "ADMIN"})
    private String role;  // "CLIENT", "DRIVER", "ADMIN"

    public RegisterRequest() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
