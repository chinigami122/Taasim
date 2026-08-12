package com.taasim.matching.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Listens for trip request events from Kafka "raw.trips".
 */
@Component
public class TripRequestConsumer {

    public TripRequestConsumer() {
        System.out.println("✅ TripRequestConsumer initialized and ready for events!");
    }

    @KafkaListener(topics = "raw.trips", groupId = "matching-service-v1")
    public void onTripRequest(@Payload String message) {
        System.out.println("==================================================");
        System.out.println("🚕 Trip received by matching-service: " + message);
        System.out.println("==================================================");
    }
}