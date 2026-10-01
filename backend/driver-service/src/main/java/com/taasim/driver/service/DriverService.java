package com.taasim.driver.service;

import com.taasim.common.util.GeoUtils;
import com.taasim.driver.kafka.TripCompletedProducer;
import com.taasim.driver.kafka.TripStatusProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages driver state: availability, assigned trips.
 */
@Service
public class DriverService {

    private final TripStatusProducer tripStatusProducer;
    private final TripCompletedProducer tripCompletedProducer;
    private final CassandraTemplate cassandraTemplate;
    private final StringRedisTemplate redisTemplate;

    // In-memory: driverId → assigned tripId (waiting for accept/reject)
    private final Map<String, String> pendingTrips = new ConcurrentHashMap<>();

    // In-memory: driverId → current active tripId (accepted, in progress)
    private final Map<String, String> activeTrips = new ConcurrentHashMap<>();

    // In-memory: driverId → availability status
    private final Map<String, String> driverStatus = new ConcurrentHashMap<>();

    // In-memory: tripId → start Instant
    private final Map<String, Instant> tripStartTimes = new ConcurrentHashMap<>();

    // In-memory: tripId → accumulated distance in km
    private final Map<String, Double> tripDistances = new ConcurrentHashMap<>();

    // In-memory: driverId → [lat, lon]
    private final Map<String, double[]> lastPositions = new ConcurrentHashMap<>();

    // In-memory: driverId → last seen Instant
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    // In-memory: tripId → clientId
    private final Map<String, String> tripClients = new ConcurrentHashMap<>();

    public DriverService(TripStatusProducer tripStatusProducer,
                         TripCompletedProducer tripCompletedProducer) {
        this(tripStatusProducer, tripCompletedProducer, null, null);
    }

    public DriverService(TripStatusProducer tripStatusProducer,
                         TripCompletedProducer tripCompletedProducer,
                         CassandraTemplate cassandraTemplate) {
        this(tripStatusProducer, tripCompletedProducer, cassandraTemplate, null);
    }

    @Autowired
    public DriverService(TripStatusProducer tripStatusProducer,
                         TripCompletedProducer tripCompletedProducer,
                         @Autowired(required = false) CassandraTemplate cassandraTemplate,
                         @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.tripStatusProducer = tripStatusProducer;
        this.tripCompletedProducer = tripCompletedProducer;
        this.cassandraTemplate = cassandraTemplate;
        this.redisTemplate = redisTemplate;
    }

    /** Called when a match event assigns a trip to this driver */
    public void assignTrip(String driverId, String tripId, int etaSeconds) {
        pendingTrips.put(driverId, tripId);
        System.out.println("📋 Driver " + driverId + " has pending trip: " + tripId);
    }

    /** Record client ID associated with a trip */
    public void registerTripClient(String tripId, String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            tripClients.put(tripId, clientId);
        }
    }

    /** Driver accepts the pending trip */
    public boolean acceptTrip(String driverId) {
        String tripId = pendingTrips.remove(driverId);
        if (tripId == null) {
            System.out.println("⚠️ No pending trip for driver " + driverId);
            return false;
        }

        activeTrips.put(driverId, tripId);
        driverStatus.put(driverId, "BUSY");

        tripStatusProducer.send(tripId, driverId, "ACCEPTED");
        System.out.println("✅ Driver " + driverId + " accepted trip " + tripId);
        return true;
    }

    /** Driver rejects the pending trip */
    public boolean rejectTrip(String driverId) {
        String tripId = pendingTrips.remove(driverId);
        if (tripId == null) return false;

        tripStatusProducer.send(tripId, driverId, "REJECTED");
        System.out.println("❌ Driver " + driverId + " rejected trip " + tripId);
        return true;
    }

    /** Get the current pending trip for a driver */
    public String getPendingTrip(String driverId) {
        return pendingTrips.get(driverId);
    }

    /** Get the current active trip for a driver */
    public String getActiveTrip(String driverId) {
        return activeTrips.get(driverId);
    }

    /** Get the driver status */
    public String getDriverStatus(String driverId) {
        return driverStatus.get(driverId);
    }

    /** Driver starts the ride (passenger picked up) */
    public boolean startRide(String driverId) {
        String tripId = activeTrips.get(driverId);
        if (tripId == null) {
            System.out.println("⚠️ No active trip for driver " + driverId);
            return false;
        }

        tripStartTimes.put(tripId, Instant.now());
        tripDistances.put(tripId, 0.0);

        tripStatusProducer.send(tripId, driverId, "IN_PROGRESS");
        System.out.println("🚗 Driver " + driverId + " started ride for trip " + tripId);
        return true;
    }

    /** Track incremental distance for an active trip from GPS telemetry */
    public void recordGpsMovement(String driverId, double lat, double lon) {
        lastSeen.put(driverId, Instant.now());
        driverStatus.putIfAbsent(driverId, "AVAILABLE");

        String tripId = activeTrips.get(driverId);
        double[] previous = lastPositions.put(driverId, new double[]{lat, lon});

        if (tripId != null && tripStartTimes.containsKey(tripId) && previous != null) {
            double deltaMeters = GeoUtils.haversineMeters(previous[0], previous[1], lat, lon);
            double deltaKm = deltaMeters / 1000.0;
            tripDistances.compute(tripId, (k, current) -> (current == null ? 0.0 : current) + deltaKm);
        }
    }

    /** Driver completes the ride (arrived at destination) */
    public boolean completeRide(String driverId) {
        String tripId = activeTrips.remove(driverId);
        if (tripId == null) {
            System.out.println("⚠️ No active trip for driver " + driverId);
            return false;
        }

        driverStatus.put(driverId, "AVAILABLE");

        Instant start = tripStartTimes.remove(tripId);
        Double distanceKm = tripDistances.remove(tripId);
        String clientId = tripClients.remove(tripId);

        double durationMin = (start != null)
                ? Duration.between(start, Instant.now()).toMillis() / 60000.0
                : 5.0;
        double finalDistKm = (distanceKm != null && distanceKm > 0.0)
                ? distanceKm
                : 5.0;

        // Send status change
        tripStatusProducer.send(tripId, driverId, "COMPLETED");

        // Send trip.completed event with real metrics for billing-service
        tripCompletedProducer.send(
                tripId,
                driverId,
                clientId != null ? clientId : "unknown",
                Math.round(finalDistKm * 1000.0) / 1000.0,
                Math.round(durationMin * 100.0) / 100.0,
                1.0
        );

        System.out.println("🏁 Driver " + driverId + " completed trip " + tripId +
                " [Dist: " + finalDistKm + " km, Duration: " + durationMin + " min]");
        return true;
    }

    /**
     * Find recent trips for a driver from Cassandra.
     */
    public java.util.List<Map<String, Object>> findTripsForDriver(String driverId, int limit) {
        if (driverId == null || driverId.isBlank()) {
            return java.util.List.of();
        }

        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();

        if (cassandraTemplate != null) {
            try {
                var rows = cassandraTemplate.getCqlOperations().queryForList(
                        "SELECT trip_id, status, taxi_id, rider_id, eta_seconds, origin_zone, dest_zone, created_at " +
                                "FROM taasim.trips WHERE taxi_id = ? LIMIT ?",
                        driverId, limit
                );

                for (Map<String, Object> r : rows) {
                    Object createdAtObj = r.get("created_at");
                    String createdAtStr = createdAtObj != null ? createdAtObj.toString() : "";
                    results.add(Map.of(
                            "tripId",     r.get("trip_id") != null ? r.get("trip_id") : "",
                            "status",     r.get("status") != null ? r.get("status") : "",
                            "driverId",   r.get("taxi_id") != null ? r.get("taxi_id") : driverId,
                            "riderId",    r.get("rider_id") != null ? r.get("rider_id") : "",
                            "etaSeconds", r.get("eta_seconds") != null ? r.get("eta_seconds") : 0,
                            "originZone", r.get("origin_zone") != null ? r.get("origin_zone") : 1,
                            "destZone",   r.get("dest_zone") != null ? r.get("dest_zone") : 1,
                            "createdAt",  createdAtStr
                    ));
                }
            } catch (Exception e) {
                System.err.println("⚠️ Error querying driver trip history from Cassandra: " + e.getMessage());
            }
        }

        // Include any active trip for this driver not yet completed
        String activeTripId = activeTrips.get(driverId);
        if (activeTripId != null && results.stream().noneMatch(m -> activeTripId.equals(m.get("tripId")))) {
            results.add(0, Map.of(
                    "tripId",     activeTripId,
                    "status",     "IN_PROGRESS",
                    "driverId",   driverId,
                    "riderId",    tripClients.getOrDefault(activeTripId, "unknown"),
                    "etaSeconds", 0,
                    "originZone", 1,
                    "destZone",   1,
                    "createdAt",  Instant.now().toString()
            ));
        }

        return results;
    }

    /**
     * List all drivers with their current status and position, with optional status filter and pagination.
     */
    public List<Map<String, Object>> listAllDrivers(String statusFilter, int page, int size) {
        Set<String> allDriverIds = new LinkedHashSet<>();
        allDriverIds.addAll(driverStatus.keySet());
        allDriverIds.addAll(lastPositions.keySet());
        allDriverIds.addAll(activeTrips.keySet());
        allDriverIds.addAll(pendingTrips.keySet());

        if (redisTemplate != null) {
            try {
                Set<String> redisDrivers = redisTemplate.opsForZSet().range("drivers:available", 0, -1);
                if (redisDrivers != null) {
                    allDriverIds.addAll(redisDrivers);
                }
            } catch (Exception ignored) {}
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String dId : allDriverIds) {
            String status = activeTrips.containsKey(dId) ? "BUSY" : driverStatus.getOrDefault(dId, "AVAILABLE");
            if (statusFilter != null && !statusFilter.isBlank() && !status.equalsIgnoreCase(statusFilter.trim())) {
                continue;
            }

            double[] pos = lastPositions.get(dId);
            Double lat = pos != null ? pos[0] : null;
            Double lon = pos != null ? pos[1] : null;

            if (pos == null && redisTemplate != null) {
                try {
                    var pointList = redisTemplate.opsForGeo().position("drivers:available", dId);
                    if (pointList != null && !pointList.isEmpty() && pointList.get(0) != null) {
                        lon = pointList.get(0).getX();
                        lat = pointList.get(0).getY();
                    }
                } catch (Exception ignored) {}
            }

            Instant seen = lastSeen.get(dId);
            String lastSeenStr = seen != null ? seen.toString() : Instant.now().toString();

            Map<String, Object> driverMap = new LinkedHashMap<>();
            driverMap.put("driverId", dId);
            driverMap.put("status", status);
            driverMap.put("activeTrip", activeTrips.get(dId));
            driverMap.put("pendingTrip", pendingTrips.get(dId));
            driverMap.put("lat", lat);
            driverMap.put("lon", lon);
            driverMap.put("lastSeen", lastSeenStr);
            result.add(driverMap);
        }

        int fromIndex = Math.max(0, page * size);
        if (fromIndex >= result.size()) {
            return List.of();
        }
        int toIndex = Math.min(result.size(), fromIndex + size);
        return result.subList(fromIndex, toIndex);
    }

    /**
     * Get detailed driver info including recent trips.
     */
    public Map<String, Object> getDriverDetail(String driverId) {
        if (driverId == null || driverId.isBlank()) {
            return null;
        }

        String status = activeTrips.containsKey(driverId) ? "BUSY" : driverStatus.getOrDefault(driverId, "OFFLINE");
        double[] pos = lastPositions.get(driverId);
        Double lat = pos != null ? pos[0] : null;
        Double lon = pos != null ? pos[1] : null;

        if (pos == null && redisTemplate != null) {
            try {
                var pointList = redisTemplate.opsForGeo().position("drivers:available", driverId);
                if (pointList != null && !pointList.isEmpty() && pointList.get(0) != null) {
                    lon = pointList.get(0).getX();
                    lat = pointList.get(0).getY();
                    if ("OFFLINE".equals(status)) {
                        status = "AVAILABLE";
                    }
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("driverId", driverId);
        detail.put("status", status);
        detail.put("activeTrip", activeTrips.get(driverId));
        detail.put("pendingTrip", pendingTrips.get(driverId));
        detail.put("lat", lat);
        detail.put("lon", lon);
        detail.put("recentTrips", findTripsForDriver(driverId, 10));
        return detail;
    }

    /**
     * Get live positions of all active/available drivers for real-time map views.
     */
    public List<Map<String, Object>> getAllLivePositions() {
        Map<String, Map<String, Object>> positionsMap = new LinkedHashMap<>();

        if (redisTemplate != null) {
            try {
                Set<String> redisDrivers = redisTemplate.opsForZSet().range("drivers:available", 0, -1);
                if (redisDrivers != null && !redisDrivers.isEmpty()) {
                    for (String dId : redisDrivers) {
                        var pointList = redisTemplate.opsForGeo().position("drivers:available", dId);
                        if (pointList != null && !pointList.isEmpty() && pointList.get(0) != null) {
                            String status = activeTrips.containsKey(dId) ? "BUSY" : "AVAILABLE";
                            positionsMap.put(dId, Map.of(
                                    "driverId", dId,
                                    "lat", pointList.get(0).getY(),
                                    "lon", pointList.get(0).getX(),
                                    "status", status
                            ));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        for (Map.Entry<String, double[]> entry : lastPositions.entrySet()) {
            String dId = entry.getKey();
            double[] coords = entry.getValue();
            String status = activeTrips.containsKey(dId) ? "BUSY" : driverStatus.getOrDefault(dId, "AVAILABLE");
            positionsMap.put(dId, Map.of(
                    "driverId", dId,
                    "lat", coords[0],
                    "lon", coords[1],
                    "status", status
            ));
        }

        return new ArrayList<>(positionsMap.values());
    }
}