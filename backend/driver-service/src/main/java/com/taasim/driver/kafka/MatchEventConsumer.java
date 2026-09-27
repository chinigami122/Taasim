package com.taasim.driver.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taasim.driver.service.DriverService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens for match events from "processed.matches".
 * When a trip is matched to this driver, store it as a pending trip assignment.
 */
@Component
public class MatchEventConsumer {

    private final DriverService driverService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MatchEventConsumer(DriverService driverService) {
        this.driverService = driverService;
    }

    @KafkaListener(topics = "processed.matches", groupId = "driver-service")
    public void onMatch(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            String tripId = json.get("trip_id").asText();
            String driverId = json.get("driver_id").asText();
            int etaSeconds = json.get("eta_seconds").asInt();

            String clientId = json.has("rider_id") ? json.get("rider_id").asText()
                    : (json.has("client_id") ? json.get("client_id").asText() : "");

            driverService.assignTrip(driverId, tripId, etaSeconds);
            if (!clientId.isBlank()) {
                driverService.registerTripClient(tripId, clientId);
            }
            System.out.println("📥 Trip assigned to driver " + driverId + ": " + tripId + " (client=" + clientId + ")");
        } catch (Exception e) {
            System.err.println("❌ Failed to process match: " + e.getMessage());
        }
    }
}