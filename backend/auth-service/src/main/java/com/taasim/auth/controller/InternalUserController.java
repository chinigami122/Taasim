package com.taasim.auth.controller;

import com.taasim.auth.model.User;
import com.taasim.auth.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Internal User", description = "Internal service-to-service endpoints")
@RestController
@RequestMapping("/api/internal/users")
public class InternalUserController {

    private final UserRepository userRepository;

    public InternalUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Operation(summary = "Get Stripe customer ID for a user", description = "Used by billing-service to charge clients")
    @GetMapping("/{id}/stripe-customer")
    public ResponseEntity<?> getStripeCustomerId(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            User user = userRepository.findById(uuid)
                    .orElse(null);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of(
                    "userId", user.getId().toString(),
                    "email", user.getEmail(),
                    "stripeCustomerId", user.getStripeCustomerId() != null ? user.getStripeCustomerId() : "",
                    "role", user.getRole().name()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user id: " + id));
        }
    }
}