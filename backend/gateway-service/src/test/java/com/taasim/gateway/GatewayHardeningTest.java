package com.taasim.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayHardeningTest {

    private static final String SECRET = "dev-only-secret-please-change-me-it-must-be-256-bits-long-abcd1234";

    @Autowired
    private WebTestClient webTestClient;

    private String generateToken(String userId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600000);
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(userId)
                .claim("email", userId + "@test.com")
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    @Test
    void correlationId_isAddedToResponse() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id");
    }

    @Test
    void publicPath_health_noToken_returns200() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void protectedPath_noToken_rejectedAtGatewayWith401() {
        webTestClient.post()
                .uri("/api/trips/request")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedPath_invalidToken_rejectedAtGatewayWith401() {
        webTestClient.post()
                .uri("/api/trips/request")
                .header("Authorization", "Bearer invalid-token-xyz")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
