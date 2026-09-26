package com.taasim.driver.service;

import com.taasim.common.util.GeoUtils;
import com.taasim.driver.dto.GpsPingRequest;
import com.taasim.driver.kafka.GpsEventProducer;
import com.taasim.driver.model.VehiclePosition;
import com.taasim.driver.repository.VehiclePositionRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * Processes incoming GPS pings from drivers.
 *
 * Saves to Cassandra, publishes to Kafka raw.gps topic, and syncs to Redis Geospatial
 * protected by a Resilience4j circuit breaker.
 */
@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final VehiclePositionRepository repository;
    private final GpsEventProducer gpsEventProducer;
    private final DriverService driverService;
    private final RestClient restClient;
    private final String geospatialServiceUrl;

    @Autowired
    public LocationService(
            VehiclePositionRepository repository,
            GpsEventProducer gpsEventProducer,
            DriverService driverService,
            @Value("${geospatial.service.url:http://localhost:8084}") String geospatialServiceUrl
    ) {
        this.repository = repository;
        this.gpsEventProducer = gpsEventProducer;
        this.driverService = driverService;
        this.geospatialServiceUrl = geospatialServiceUrl;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(1500);

        this.restClient = RestClient.builder()
                .baseUrl(geospatialServiceUrl)
                .requestFactory(factory)
                .build();
    }

    public LocationService(VehiclePositionRepository repository,
                           GpsEventProducer gpsEventProducer,
                           DriverService driverService,
                           RestClient restClient,
                           String geospatialServiceUrl) {
        this.repository = repository;
        this.gpsEventProducer = gpsEventProducer;
        this.driverService = driverService;
        this.restClient = restClient;
        this.geospatialServiceUrl = geospatialServiceUrl;
    }

    /**
     * Process a GPS ping from a driver.
     *
     * 1. Determine which zone the driver is in
     * 2. Save to Cassandra vehicle_positions table
     * 3. Publish to Kafka raw.gps topic
     * 4. Sync to Redis Geospatial index (Circuit breaker protected)
     */
    public VehiclePosition processGpsPing(GpsPingRequest request) {
        int zoneId = GeoUtils.calculateZoneId(request.getLat(), request.getLon());
        String zoneName = "Zone-" + zoneId;

        VehiclePosition position = new VehiclePosition();
        position.setCity("casablanca");
        position.setZoneId(zoneId);
        position.setZoneName(zoneName);
        position.setEventTime(Instant.now());
        position.setTaxiId(request.getDriverId());
        position.setLat(request.getLat());
        position.setLon(request.getLon());
        position.setSpeed(request.getSpeed());
        position.setStatus("available");

        // ── 1. Save to Cassandra ──
        repository.save(position);

        // ── 2. Publish to Kafka ──
        gpsEventProducer.send(
                request.getDriverId(),
                request.getLat(),
                request.getLon(),
                request.getSpeed(),
                Instant.now().toEpochMilli()
        );

        // ── 3. Sync to Redis Geospatial Index ──
        syncToGeospatial(request.getDriverId(), request.getLat(), request.getLon());

        // ── 4. Track Ride Distance in DriverService if ride is in progress ──
        if (driverService != null) {
            driverService.recordGpsMovement(request.getDriverId(), request.getLat(), request.getLon());
        }

        System.out.println("📍 Saved GPS: " + request.getDriverId()
                + " → Zone " + zoneId
                + " (" + request.getLat() + ", " + request.getLon() + ")");

        return position;
    }

    @CircuitBreaker(name = "geospatial-write", fallbackMethod = "fallbackGeospatialSync")
    public void syncToGeospatial(String driverId, double lat, double lon) {
        if (geospatialServiceUrl != null && !geospatialServiceUrl.isBlank()) {
            restClient.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/drivers/{driverId}/position")
                            .queryParam("lat", lat)
                            .queryParam("lon", lon)
                            .build(driverId))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    public void fallbackGeospatialSync(String driverId, double lat, double lon, Throwable t) {
        log.warn("⚠️ Geospatial sync circuit open/failed for driver {}: {}", driverId, t.getMessage());
    }
}
