package com.taasim.matching.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes match events to Kafka "processed.matches".
 * Both trip-service and driver-service will consume these.
 */
@Component
public class MatchEventProducer {

    private static final String TOPIC = "processed.matches";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public MatchEventProducer(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(String tripId, String driverId, double distanceMeters,
                     int etaSeconds, int originZone, int destinationZone) {
        Map<String, Object> event = Map.of(
                "trip_id", tripId,
                "driver_id", driverId,
                "distance_meters", distanceMeters,
                "eta_seconds", etaSeconds,
                "origin_zone", originZone,
                "destination_zone", destinationZone,
                "matched_at", System.currentTimeMillis()
        );

        kafkaTemplate.send(TOPIC, tripId, event);
        System.out.println("📡 Kafka → processed.matches: trip=" + tripId + " → driver=" + driverId);
    }
}
