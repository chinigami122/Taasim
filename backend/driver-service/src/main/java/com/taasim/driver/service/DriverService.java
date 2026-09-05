package com.taasim.driver.service;

import com.taasim.driver.kafka.TripCompletedProducer;
import com.taasim.driver.kafka.TripStatusProducer;
import org.springframework.stereotype.Service;

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

        tripStatusProducer.send(tripId, driverId, "IN_PROGRESS");
        System.out.println("🚗 Driver " + driverId + " started ride for trip " + tripId);
        return true;
    }

    /** Driver completes the ride (arrived at destination) */
    public boolean completeRide(String driverId) {
        String tripId = activeTrips.remove(driverId);
        if (tripId == null) {
            System.out.println("⚠️ No active trip for driver " + driverId);
            return false;
        }

        driverStatus.put(driverId, "AVAILABLE");

        // Send status change
        tripStatusProducer.send(tripId, driverId, "COMPLETED");

        // Send trip.completed event (for billing service in Slice 15)
        tripCompletedProducer.send(tripId, driverId);

        System.out.println("🏁 Driver " + driverId + " completed trip " + tripId);
        return true;
    }
}