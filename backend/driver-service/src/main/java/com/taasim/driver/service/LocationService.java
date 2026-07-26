package com.taasim.driver.service;

import com.taasim.driver.dto.GpsPingRequest;
import com.taasim.driver.model.VehiclePosition;
import com.taasim.driver.repository.VehiclePositionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Processes incoming GPS pings from drivers.
 *
 * For now (Slice 1): saves to Cassandra only.
 * Slice 2 will add: publish to Kafka raw.gps topic.
 */
@Service
public class LocationService {

    private final VehiclePositionRepository repository;

    // Constructor injection — Spring auto-wires the repository
    public LocationService(VehiclePositionRepository repository) {
        this.repository = repository;
    }

    /**
     * Process a GPS ping from a driver.
     *
     * 1. Determine which zone the driver is in (simplified for now)
     * 2. Build a VehiclePosition entity
     * 3. Save to Cassandra vehicle_positions table
     */
    public VehiclePosition processGpsPing(GpsPingRequest request) {

        // ── Zone Lookup (simplified) ──
        // In the full system, Flink Job 1 (gps_job.py) does a proper
        // Shapely point-in-polygon lookup against the Casablanca GeoJSON.
        // For now, we use a simple grid (same formula as trip_request_producer.py).
        // This will be improved in Slice 13 (Geospatial Service with Redis).
        int zoneId = calculateZoneId(request.getLat(), request.getLon());
        String zoneName = "Zone-" + zoneId;  // Simplified — proper names come later

        // ── Build the entity ──
        VehiclePosition position = new VehiclePosition();
        position.setCity("casablanca");
        position.setZoneId(zoneId);
        position.setZoneName(zoneName);
        position.setEventTime(Instant.now());
        position.setTaxiId(request.getDriverId());
        position.setLat(request.getLat());
        position.setLon(request.getLon());
        position.setSpeed(request.getSpeed());
        position.setStatus("available");  // Default status for now

        // ── Save to Cassandra ──
        repository.save(position);

        System.out.println("📍 Saved GPS: " + request.getDriverId()
                + " → Zone " + zoneId
                + " (" + request.getLat() + ", " + request.getLon() + ")");

        return position;
    }

    /**
     * Simplified zone calculation using a 4×4 grid over Casablanca.
     * Same formula used in the existing Python simulators.
     *
     * Casablanca bounds (from OSMnx road network):
     *   LON: -7.6895 to -7.4008
     *   LAT: 33.5072 to 33.6527
     *
     * Grid cell index formula (matches gps_job.py fallback):
     *   grid_x = int(4 * (lon - LON_MIN) / (LON_MAX - LON_MIN))
     *   grid_y = int(4 * (lat - LAT_MIN) / (LAT_MAX - LAT_MIN))
     *   zone_id = (grid_y * 4) + grid_x + 1   → range 1..16
     */
    private int calculateZoneId(double lat, double lon) {
        double LON_MIN = -7.6895, LON_MAX = -7.4008;
        double LAT_MIN = 33.5072, LAT_MAX = 33.6527;

        int gridX = (int) (4 * (lon - LON_MIN) / (LON_MAX - LON_MIN));
        int gridY = (int) (4 * (lat - LAT_MIN) / (LAT_MAX - LAT_MIN));

        // Clamp to valid range
        gridX = Math.max(0, Math.min(3, gridX));
        gridY = Math.max(0, Math.min(3, gridY));

        return (gridY * 4) + gridX + 1;  // 1-indexed, range 1..16
    }
}