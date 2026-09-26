package com.taasim.billing.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taasim.billing.service.BillingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TripCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TripCompletedConsumer.class);

    private final BillingService billingService;
    private final ObjectMapper objectMapper;

    public TripCompletedConsumer(BillingService billingService, ObjectMapper objectMapper) {
        this.billingService = billingService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "trip.completed", groupId = "${spring.kafka.consumer.group-id:billing-service}")
    public void onCompleted(String message) {
        try {
            log.info("📥 Kafka received trip.completed: {}", message);
            JsonNode j = objectMapper.readTree(message);

            String tripId    = j.has("trip_id") ? j.get("trip_id").asText() : j.get("tripId").asText();
            String driverId  = j.has("driver_id") ? j.get("driver_id").asText() : j.get("driverId").asText();
            String clientId  = j.has("client_id") ? j.get("client_id").asText()
                    : (j.has("rider_id") ? j.get("rider_id").asText() : "unknown");

            double distance  = j.has("distance_km") ? j.get("distance_km").asDouble()
                    : (j.has("distanceKm") ? j.get("distanceKm").asDouble() : 5.0);

            double duration  = j.has("duration_min") ? j.get("duration_min").asDouble()
                    : (j.has("durationMin") ? j.get("durationMin").asDouble() : 10.0);

            double surge     = j.has("surge") ? j.get("surge").asDouble()
                    : (j.has("surge_multiplier") ? j.get("surge_multiplier").asDouble() : 1.0);

            billingService.createBillingFor(tripId, driverId, clientId, distance, duration, surge);
        } catch (Exception e) {
            log.error("❌ Billing failed for message: {} | Error: {}", message, e.getMessage(), e);
        }
    }
}
