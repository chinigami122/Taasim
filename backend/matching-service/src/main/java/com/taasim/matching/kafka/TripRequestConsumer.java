package com.taasim.matching.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taasim.matching.service.MatchingEngine;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TripRequestConsumer {

    private final MatchingEngine matchingEngine;
    private final MatchEventProducer matchEventProducer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TripRequestConsumer(MatchingEngine matchingEngine, MatchEventProducer matchEventProducer) {
        this.matchingEngine = matchingEngine;
        this.matchEventProducer = matchEventProducer;
    }

    @KafkaListener(topics = "raw.trips", groupId = "matching-service")
    public void onTripRequest(String message) {
        System.out.println("🚕 Trip received: " + message);

        try {
            JsonNode json = objectMapper.readTree(message);
            String tripId = json.get("trip_id").asText();
            int originZone = json.has("origin_zone") ? json.get("origin_zone").asInt() : 1;
            int destinationZone = json.has("destination_zone") ? json.get("destination_zone").asInt() : 1;

            Double originLat = json.hasNonNull("origin_lat") ? json.get("origin_lat").asDouble() : null;
            Double originLon = json.hasNonNull("origin_lon") ? json.get("origin_lon").asDouble() : null;

            // Find nearest driver prioritizing exact GPS coordinates with zone fallback
            Map<String, Object> match = matchingEngine.findNearestDriver(originLat, originLon, originZone);

            if (match != null) {
                matchEventProducer.send(
                        tripId,
                        (String) match.get("driverId"),
                        (double) match.get("distanceMeters"),
                        (int) match.get("etaSeconds"),
                        originZone,
                        destinationZone
                );
            }
        } catch (JsonProcessingException e) {
            System.err.println("❌ Failed to parse trip request: " + e.getMessage());
        }
    }
}
