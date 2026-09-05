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
        Map<String, Object> event = Map.of(
                "trip_id", tripId,
                "driver_id", driverId,
                "completed_at", System.currentTimeMillis(),
                "distance_km", 5.2,    // TODO: calculate real distance from GPS track
                "duration_min", 12.0   // TODO: calculate from start->complete timestamps
        );

        kafkaTemplate.send(TOPIC, tripId, event);
        System.out.println("📡 Kafka → trip.completed: " + tripId);
    }
}
