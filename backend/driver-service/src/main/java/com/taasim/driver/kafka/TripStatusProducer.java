package com.taasim.driver.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes trip status events (ACCEPTED, REJECTED, STARTED, COMPLETED)
 * to the "trip.status" topic.
 */
@Component
public class TripStatusProducer {

    private static final String TOPIC = "trip.status";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public TripStatusProducer(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(String tripId, String driverId, String newStatus) {
        Map<String, Object> event = Map.of(
                "trip_id", tripId,
                "driver_id", driverId,
                "status", newStatus,
                "timestamp", System.currentTimeMillis()
        );

        kafkaTemplate.send(TOPIC, tripId, event);
        System.out.println("📡 Kafka → trip.status: " + tripId + " → " + newStatus);
    }
}