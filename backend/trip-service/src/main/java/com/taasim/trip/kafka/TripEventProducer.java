package com.taasim.trip.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes trip request events to Kafka "raw.trips".
 *
 * Forwards origin/destination zones and exact coordinates when available.
 */
@Component
public class TripEventProducer {

    private static final String TOPIC = "raw.trips";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public TripEventProducer(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(String tripId, String riderId, int originZone, int destinationZone,
                     Double originLat, Double originLon, Double destLat, Double destLon,
                     long requestedAt) {
        Map<String, Object> event = new HashMap<>();
        event.put("trip_id", tripId);
        event.put("rider_id", riderId);
        event.put("origin_zone", originZone);
        event.put("destination_zone", destinationZone);
        event.put("requested_at", requestedAt);
        event.put("call_type", "A");

        if (originLat != null) event.put("origin_lat", originLat);
        if (originLon != null) event.put("origin_lon", originLon);
        if (destLat != null) event.put("dest_lat", destLat);
        if (destLon != null) event.put("dest_lon", destLon);

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