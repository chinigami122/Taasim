package com.taasim.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private static final String SECRET = "dev-only-secret-please-change-me-it-must-be-256-bits-long-abcd1234";
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils(SECRET);
    }

    private String createToken(String userId, String email, String role, long validityMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMs);
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    @Test
    void parseValidToken_extractsClaimsCorrectly() {
        String token = createToken("user-123", "driver@test.com", "DRIVER", 60000);

        assertThat(jwtUtils.isValid(token)).isTrue();
        assertThat(jwtUtils.getUserId(token)).isEqualTo("user-123");
        assertThat(jwtUtils.getEmail(token)).isEqualTo("driver@test.com");
        assertThat(jwtUtils.getRole(token)).isEqualTo("DRIVER");
    }

    @Test
    void parseExpiredToken_returnsInvalid() {
        // Expired 10 seconds ago
        String expiredToken = createToken("user-123", "driver@test.com", "DRIVER", -10000);

        assertThat(jwtUtils.isValid(expiredToken)).isFalse();
    }

    @Test
    void parseTamperedToken_returnsInvalid() {
        String token = createToken("user-123", "driver@test.com", "DRIVER", 60000);
        String tampered = token + "xyz";

        assertThat(jwtUtils.isValid(tampered)).isFalse();
    }

    @Test
    void parseTokenWithDifferentSecret_returnsInvalid() {
        JwtUtils otherUtils = new JwtUtils("another-secret-that-is-at-least-256-bits-long-1234567890123456");
        String token = createToken("user-123", "driver@test.com", "DRIVER", 60000);

        assertThat(otherUtils.isValid(token)).isFalse();
    }
}
