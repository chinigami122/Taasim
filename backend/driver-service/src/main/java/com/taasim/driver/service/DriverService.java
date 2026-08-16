package com.taasim.driver.service;

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

    // In-memory: driverId → assigned tripId (waiting for accept/reject)
    private final Map<String, String> pendingTrips = new ConcurrentHashMap<>();

    // In-memory: driverId → current active tripId (accepted, in progress)
    private final Map<String, String> activeTrips = new ConcurrentHashMap<>();

    // In-memory: driverId → availability status
    private final Map<String, String> driverStatus = new ConcurrentHashMap<>();

    public DriverService(TripStatusProducer tripStatusProducer) {
        this.tripStatusProducer = tripStatusProducer;
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
}