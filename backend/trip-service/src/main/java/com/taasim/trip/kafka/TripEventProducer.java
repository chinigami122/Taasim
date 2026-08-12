package com.taasim.trip.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes trip request events to Kafka "raw.trips".
 *
 * The existing Flink Job 3 (trip_matcher_job.py) and the
 * matching-service (Slice 4+) consume from this topic.
 *
 * JSON schema matches the Python trip_request_producer.py format.
 */
@Component
public class TripEventProducer {

    private static final String TOPIC = "raw.trips";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public TripEventProducer(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(String tripId, String riderId, int originZone,
                     int destinationZone, long requestedAt) {
        Map<String, Object> event = Map.of(
                "trip_id", tripId,
                "rider_id", riderId,
                "origin_zone", originZone,
                "destination_zone", destinationZone,
                "requested_at", requestedAt,
                "call_type", "A"   // Central booking (same as existing Python producer)
        );

        kafkaTemplate.send(TOPIC, String.valueOf(originZone), event).whenComplete((result, ex) -> {
            if (ex != null) {
                System.err.println("❌ Kafka send error (raw.trips): " + ex.getMessage());
                ex.printStackTrace();
            } else {
                System.out.println("✅ Kafka send success (raw.trips) to partition " 
                        + result.getRecordMetadata().partition() 
                        + " @ offset " + result.getRecordMetadata().offset());
            }
        });
        System.out.println("📡 Kafka → raw.trips: " + tripId);
    }
}