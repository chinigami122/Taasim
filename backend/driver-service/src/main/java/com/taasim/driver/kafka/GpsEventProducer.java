package com.taasim.driver.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes GPS events to the Kafka "raw.gps" topic.
 *
 * The existing Flink Job 1 (gps_job.py) consumes from this topic,
 * enriches with real zone boundaries (Shapely), and writes to
 * Cassandra + processed.gps.
 *
 * This producer emits the SAME JSON schema as the Python
 * vehicle_gps_producer.py simulator, so Flink processes it identically.
 */
@Component
public class GpsEventProducer {

    private static final String TOPIC = "raw.gps";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public GpsEventProducer(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish a GPS event to Kafka.
     *
     * @param driverId  the taxi/driver ID (used as Kafka message key for partitioning)
     * @param lat       latitude
     * @param lon       longitude
     * @param speed     speed in km/h
     * @param timestamp epoch milliseconds
     */
    public void send(String driverId, double lat, double lon, double speed, long timestamp) {
        Map<String, Object> event = Map.of(
                "taxi_id", driverId,
                "lat", lat,
                "lon", lon,
                "speed", speed,
                "timestamp", timestamp,
                "status", "available"
        );

        kafkaTemplate.send(TOPIC, driverId, event).whenComplete((result, ex) -> {
            if (ex != null) {
                System.err.println("❌ Kafka send error: " + ex.getMessage());
                ex.printStackTrace();
            } else {
                System.out.println("✅ Kafka send success to partition " 
                        + result.getRecordMetadata().partition() 
                        + " @ offset " + result.getRecordMetadata().offset());
            }
        });
        System.out.println("📡 Kafka → raw.gps: " + driverId);
    }
}