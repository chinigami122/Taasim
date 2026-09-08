package com.taasim.auth.controller;

import com.taasim.auth.dto.AuthResponse;
import com.taasim.auth.dto.LoginRequest;
import com.taasim.auth.dto.RegisterRequest;
import com.taasim.auth.model.User;
import com.taasim.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Authentication", description = "User registration and login endpoints")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user", description = "Creates a new user account with hashed password.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User successfully registered"),
        @ApiResponse(responseCode = "400", description = "Validation error or duplicate email")
    })
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            User user = authService.register(request);
            return ResponseEntity.ok(Map.of(
                    "id", user.getId().toString(),
                    "email", user.getEmail(),
                    "fullName", user.getFullName(),
                    "role", user.getRole().name(),
                    "message", "Registration successful"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Authenticate user and get JWT", description = "Validates credentials and returns a signed JWT token.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful, returns Bearer token"),
        @ApiResponse(responseCode = "401", description = "Invalid email or password")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }
}
