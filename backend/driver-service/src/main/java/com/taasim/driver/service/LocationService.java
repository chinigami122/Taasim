package com.taasim.driver.service;

import com.taasim.driver.dto.GpsPingRequest;
import com.taasim.driver.kafka.GpsEventProducer;
import com.taasim.driver.model.VehiclePosition;
import com.taasim.driver.repository.VehiclePositionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Processes incoming GPS pings from drivers.
 *
 * Saves to Cassandra, publishes to Kafka raw.gps topic, and syncs to Redis Geospatial.
 */
@Service
public class LocationService {

    private final VehiclePositionRepository repository;
    private final GpsEventProducer gpsEventProducer;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${geospatial.service.url:http://localhost:8084}")
    private String geospatialServiceUrl;

    // Constructor injection — Spring auto-wires dependencies
    public LocationService(VehiclePositionRepository repository,
                           GpsEventProducer gpsEventProducer) {
        this.repository = repository;
        this.gpsEventProducer = gpsEventProducer;
    }

    /**
     * Process a GPS ping from a driver.
     *
     * 1. Determine which zone the driver is in
     * 2. Save to Cassandra vehicle_positions table
     * 3. Publish to Kafka raw.gps topic
     */
    public VehiclePosition processGpsPing(GpsPingRequest request) {

        int zoneId = calculateZoneId(request.getLat(), request.getLon());
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
        try {
            if (geospatialServiceUrl != null && !geospatialServiceUrl.isBlank()) {
                String url = geospatialServiceUrl + "/internal/drivers/" + request.getDriverId()
                        + "/position?lat=" + request.getLat() + "&lon=" + request.getLon();
                restTemplate.put(url, null);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Geospatial update failed for driver " + request.getDriverId() + ": " + e.getMessage());
        }

        System.out.println("📍 Saved GPS: " + request.getDriverId()
                + " → Zone " + zoneId
                + " (" + request.getLat() + ", " + request.getLon() + ")");

        return position;
    }

    private int calculateZoneId(double lat, double lon) {
        double LON_MIN = -7.6895, LON_MAX = -7.4008;
        double LAT_MIN = 33.5072, LAT_MAX = 33.6527;
        int gridX = (int) (4 * (lon - LON_MIN) / (LON_MAX - LON_MIN));
        int gridY = (int) (4 * (lat - LAT_MIN) / (LAT_MAX - LAT_MIN));
        gridX = Math.max(0, Math.min(3, gridX));
        gridY = Math.max(0, Math.min(3, gridY));
        return (gridY * 4) + gridX + 1;
    }
}
