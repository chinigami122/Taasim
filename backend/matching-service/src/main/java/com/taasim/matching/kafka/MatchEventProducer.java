package com.taasim.matching.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
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
        send(tripId, driverId, null, distanceMeters, etaSeconds, originZone, destinationZone);
    }

    public void send(String tripId, String driverId, String riderId, double distanceMeters,
                     int etaSeconds, int originZone, int destinationZone) {
        Map<String, Object> event = new HashMap<>();
        event.put("trip_id", tripId);
        event.put("driver_id", driverId);
        if (riderId != null && !riderId.isBlank()) {
            event.put("rider_id", riderId);
        }
        event.put("distance_meters", distanceMeters);
        event.put("eta_seconds", etaSeconds);
        event.put("origin_zone", originZone);
        event.put("destination_zone", destinationZone);
        event.put("matched_at", System.currentTimeMillis());

        kafkaTemplate.send(TOPIC, tripId, event);
        System.out.println("📡 Kafka → processed.matches: trip=" + tripId + " → driver=" + driverId + " (rider=" + riderId + ")");
    }
}
