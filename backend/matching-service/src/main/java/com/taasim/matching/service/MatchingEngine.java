package com.taasim.matching.service;

import com.taasim.matching.util.GeoUtils;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Core matching logic.
 * Finds the nearest available driver to a trip's origin zone.
 *
 * For Slice 5: queries Cassandra vehicle_positions directly.
 * Slice 14 will switch to Redis via the Geospatial Service for faster queries.
 */
@Service
public class MatchingEngine {

    private final CassandraTemplate cassandraTemplate;

    public MatchingEngine(CassandraTemplate cassandraTemplate) {
        this.cassandraTemplate = cassandraTemplate;
    }

    /**
     * Find the nearest driver to a given zone.
     *
     * @param originZone the pickup zone ID (1-16)
     * @return a map with driverId, distanceMeters, etaSeconds — or null if no drivers found
     */
    public Map<String, Object> findNearestDriver(int originZone) {
        // Get the center of the origin zone
        double[] center = GeoUtils.getZoneCenter(originZone);
        double pickupLat = center[0];
        double pickupLon = center[1];

        // Query recent driver positions from Cassandra
        // We check the origin zone AND neighboring zones
        List<Map<String, Object>> drivers = cassandraTemplate.getCqlOperations().queryForList(
                "SELECT taxi_id, lat, lon FROM taasim.vehicle_positions " +
                        "WHERE city = 'casablanca' AND zone_id = ? LIMIT 50",
                originZone
        );

        // If no drivers in the exact zone, check nearby zones
        if (drivers.isEmpty()) {
            for (int delta : new int[]{-1, 1, -4, 4}) {
                int neighborZone = originZone + delta;
                if (neighborZone >= 1 && neighborZone <= 16) {
                    drivers = cassandraTemplate.getCqlOperations().queryForList(
                            "SELECT taxi_id, lat, lon FROM taasim.vehicle_positions " +
                                    "WHERE city = 'casablanca' AND zone_id = ? LIMIT 50",
                            neighborZone
                    );
                    if (!drivers.isEmpty()) break;
                }
            }
        }

        if (drivers.isEmpty()) {
            System.out.println("❌ No drivers found near zone " + originZone);
            return null;
        }

        // Find the closest driver (Haversine)
        String bestDriverId = null;
        double bestDistance = Double.MAX_VALUE;

        for (Map<String, Object> driver : drivers) {
            String driverId = (String) driver.get("taxi_id");
            double driverLat = (double) driver.get("lat");
            double driverLon = (double) driver.get("lon");

            double distance = GeoUtils.haversine(pickupLat, pickupLon, driverLat, driverLon);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestDriverId = driverId;
            }
        }

        int eta = GeoUtils.computeEta(bestDistance);

        System.out.println("✅ Matched: " + bestDriverId
                + " | Distance: " + (int) bestDistance + "m"
                + " | ETA: " + eta + "s");

        return Map.of(
                "driverId", bestDriverId,
                "distanceMeters", bestDistance,
                "etaSeconds", eta
        );
    }
}