package com.taasim.trip.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taasim.trip.service.TripService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens for trip status changes from "trip.status".
 * Handles: ACCEPTED, REJECTED, IN_PROGRESS, COMPLETED
 */
@Component
public class TripStatusConsumer {

    private final TripService tripService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TripStatusConsumer(TripService tripService) {
        this.tripService = tripService;
    }

    @KafkaListener(topics = "trip.status", groupId = "trip-service")
    public void onTripStatus(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            String tripId = json.get("trip_id").asText();
            String status = json.get("status").asText();
            String driverId = json.get("driver_id").asText();

            tripService.updateTripStatus(tripId, status);
            System.out.println("🔄 Trip " + tripId + " → " + status);
        } catch (Exception e) {
            System.err.println("❌ Failed to process trip status: " + e.getMessage());
        }
    }
}