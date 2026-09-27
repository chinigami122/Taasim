package com.taasim.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class ClientLookupService {

    private static final Logger log = LoggerFactory.getLogger(ClientLookupService.class);

    private final String authBaseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ClientLookupService(
            @Value("${auth-service.base-url:http://localhost:8081}") String authBaseUrl,
            ObjectMapper objectMapper) {
        this.authBaseUrl = authBaseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Fetch stripe_customer_id from auth-service for the given userId.
     * Returns null/blank if not found or auth-service unavailable.
     * This is intentionally non-throwing — caller should handle missing customer gracefully.
     */
    public String getStripeCustomerId(String clientId) {
        if (clientId == null || clientId.isBlank() || "unknown".equals(clientId)) {
            log.warn("⚠️ Cannot lookup Stripe customer: clientId is blank/unknown");
            return null;
        }
        try {
            String url = authBaseUrl.replaceAll("/$", "") + "/api/internal/users/" + clientId + "/stripe-customer";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("⚠️ ClientLookup non-200 for {}: {} body={}", clientId, response.statusCode(), response.body());
                return null;
            }
            JsonNode node = objectMapper.readTree(response.body());
            String stripeId = node.has("stripeCustomerId") ? node.get("stripeCustomerId").asText() : null;
            if (stripeId == null || stripeId.isBlank()) {
                log.warn("⚠️ No stripeCustomerId for client {}", clientId);
                return null;
            }
            return stripeId;
        } catch (Exception e) {
            log.warn("⚠️ ClientLookup failed for {}: {}", clientId, e.getMessage());
            return null;
        }
    }
}