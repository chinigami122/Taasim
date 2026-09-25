package com.taasim.matching.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taasim.common.util.GeoUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Core matching logic.
 * Finds the nearest available driver to a trip's pickup location using Redis Geospatial indexing,
 * protected with Resilience4j CircuitBreaker and Retry mechanisms.
 */
@Service
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String geospatialBaseUrl;
    private final double searchRadiusMeters;

    @Autowired
    public MatchingEngine(
            @Value("${geospatial.service.url:http://localhost:8084}") String geospatialBaseUrl,
            @Value("${matching.search-radius-meters:5000}") double searchRadiusMeters,
            ObjectMapper objectMapper
    ) {
        this.geospatialBaseUrl = geospatialBaseUrl;
        this.searchRadiusMeters = searchRadiusMeters;
        this.objectMapper = objectMapper;

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1500);
        requestFactory.setReadTimeout(2000);

        this.restClient = RestClient.builder()
                .baseUrl(geospatialBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public MatchingEngine(RestClient restClient, ObjectMapper objectMapper, String geospatialBaseUrl, double searchRadiusMeters) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.geospatialBaseUrl = geospatialBaseUrl;
        this.searchRadiusMeters = searchRadiusMeters;
    }

    /**
     * Overloaded method for backward compatibility with zone-only calls.
     */
    public Map<String, Object> findNearestDriver(int originZone) {
        return findNearestDriver(null, null, originZone);
    }

    /**
     * Find the nearest available driver to exact coordinates or zone center using Redis Geospatial.
     * Decorated with Resilience4j Circuit Breaker & Retry.
     */
    @CircuitBreaker(name = "geospatial", fallbackMethod = "fallbackNearestDriver")
    @Retry(name = "geospatial")
    public Map<String, Object> findNearestDriver(Double originLat, Double originLon, Integer originZone) {
        double pickupLat;
        double pickupLon;

        if (originLat != null && originLon != null) {
            pickupLat = originLat;
            pickupLon = originLon;
        } else if (originZone != null && originZone >= 1 && originZone <= 16) {
            double[] center = GeoUtils.getZoneCenter(originZone);
            pickupLat = center[0];
            pickupLon = center[1];
        } else {
            double[] center = GeoUtils.getZoneCenter(1);
            pickupLat = center[0];
            pickupLon = center[1];
        }

        String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/drivers/nearby")
                        .queryParam("lat", pickupLat)
                        .queryParam("lon", pickupLon)
                        .queryParam("radius", searchRadiusMeters)
                        .build())
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            log.warn("❌ Empty response from geospatial service for coordinates ({}, {})", pickupLat, pickupLon);
            return null;
        }

        try {
            List<Map<String, Object>> drivers = objectMapper.readValue(
                    response,
                    new TypeReference<>() {}
            );

            if (drivers == null || drivers.isEmpty()) {
                log.info("ℹ️ No drivers found near ({}, {}) within {}m", pickupLat, pickupLon, (int) searchRadiusMeters);
                return null;
            }

            // Results from Redis GEO are already sorted ascending by proximity
            Map<String, Object> closest = drivers.get(0);
            String driverId = (String) closest.get("driverId");
            double distanceMeters = ((Number) closest.get("distanceMeters")).doubleValue();
            int eta = GeoUtils.computeEta(distanceMeters);

            log.info("✅ Matched via Redis: {} | Distance: {}m | ETA: {}s",
                    driverId, (int) distanceMeters, eta);

            return Map.of(
                    "driverId", driverId,
                    "distanceMeters", distanceMeters,
                    "etaSeconds", eta
            );
        } catch (Exception e) {
            log.error("❌ Failed to parse geospatial response: {}", e.getMessage());
            throw new RuntimeException("Geospatial parsing error", e);
        }
    }

    /**
     * Fallback method executed when the circuit is OPEN or retries are exhausted.
     */
    public Map<String, Object> fallbackNearestDriver(Double originLat, Double originLon, Integer originZone, Throwable t) {
        log.warn("⚠️ Geospatial service fallback triggered! Reason: {}", t.getMessage());
        return null;
    }
}