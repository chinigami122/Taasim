package com.taasim.trip.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taasim.trip.service.TripService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens for match events from "processed.matches".
 * When a trip is matched to a driver, update the trip status.
 */
@Component
public class MatchEventConsumer {

    private final TripService tripService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MatchEventConsumer(TripService tripService) {
        this.tripService = tripService;
    }

    @KafkaListener(topics = "processed.matches", groupId = "trip-service")
    public void onMatch(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            String tripId = json.get("trip_id").asText();
            String driverId = json.get("driver_id").asText();
            int etaSeconds = json.get("eta_seconds").asInt();

            tripService.updateTripToMatched(tripId, driverId, etaSeconds);
            System.out.println("🔗 Trip " + tripId + " matched to driver " + driverId);
        } catch (Exception e) {
            System.err.println("❌ Failed to process match event: " + e.getMessage());
        }
    }
}