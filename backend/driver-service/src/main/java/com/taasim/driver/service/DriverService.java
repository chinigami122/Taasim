package com.taasim.driver.service;

import com.taasim.common.util.GeoUtils;
import com.taasim.driver.kafka.TripCompletedProducer;
import com.taasim.driver.kafka.TripStatusProducer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages driver state: availability, assigned trips.
 */
@Service
public class DriverService {

    private final TripStatusProducer tripStatusProducer;
    private final TripCompletedProducer tripCompletedProducer;

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

    // In-memory: tripId → clientId
    private final Map<String, String> tripClients = new ConcurrentHashMap<>();

    public DriverService(TripStatusProducer tripStatusProducer,
                         TripCompletedProducer tripCompletedProducer) {
        this.tripStatusProducer = tripStatusProducer;
        this.tripCompletedProducer = tripCompletedProducer;
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
}