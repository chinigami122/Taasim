package com.taasim.matching.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taasim.matching.util.GeoUtils;
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
 * Finds the nearest available driver to a trip's pickup origin zone using Redis Geospatial indexing.
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
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);

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
     * Find the nearest available driver to a given zone using Redis Geospatial service.
     *
     * @param originZone the pickup zone ID (1-16)
     * @return a map with driverId, distanceMeters, etaSeconds — or null if no drivers found
     */
    public Map<String, Object> findNearestDriver(int originZone) {
        double[] center = GeoUtils.getZoneCenter(originZone);
        double lat = center[0];
        double lon = center[1];

        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/drivers/nearby")
                            .queryParam("lat", lat)
                            .queryParam("lon", lon)
                            .queryParam("radius", searchRadiusMeters)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                log.warn("❌ Empty response from geospatial service for zone {}", originZone);
                return null;
            }

            List<Map<String, Object>> drivers = objectMapper.readValue(
                    response,
                    new TypeReference<>() {}
            );

            if (drivers == null || drivers.isEmpty()) {
                log.info("ℹ️ No drivers found near zone {} within {}m", originZone, (int) searchRadiusMeters);
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
            log.error("❌ Failed to query geospatial service at {}: {}", geospatialBaseUrl, e.getMessage());
            return null;
        }
    }
}