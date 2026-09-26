package com.taasim.driver.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes to "trip.completed" when a ride finishes.
 * The billing-service (Slice 15) will consume this to calculate fares.
 */
@Component
public class TripCompletedProducer {

    private static final String TOPIC = "trip.completed";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public TripCompletedProducer(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(String tripId, String driverId) {
        send(tripId, driverId, "unknown", 5.0, 10.0, 1.0);
    }

    public void send(String tripId, String driverId, double distanceKm, double durationMin) {
        send(tripId, driverId, "unknown", distanceKm, durationMin, 1.0);
    }

    public void send(String tripId, String driverId, String clientId, double distanceKm, double durationMin, double surge) {
        Map<String, Object> event = Map.of(
                "trip_id", tripId,
                "driver_id", driverId,
                "client_id", clientId != null ? clientId : "unknown",
                "completed_at", System.currentTimeMillis(),
                "distance_km", distanceKm,
                "duration_min", durationMin,
                "surge", surge
        );

        kafkaTemplate.send(TOPIC, tripId, event);
        System.out.println("📡 Kafka → trip.completed: " + tripId + " (" + distanceKm + " km, " + durationMin + " min)");
    }
}
