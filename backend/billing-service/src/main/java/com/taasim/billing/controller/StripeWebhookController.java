package com.taasim.billing.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import com.taasim.billing.model.BillingRecord;
import com.taasim.billing.repository.BillingRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Optional;

@Tag(name = "Stripe Webhook", description = "Asynchronous payment webhook receiver")
@RestController
@RequestMapping("/api/billing/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final BillingRecordRepository repository;

    @Value("${stripe.webhook-secret:whsec_placeholder}")
    private String webhookSecret;

    public StripeWebhookController(BillingRecordRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Handle Stripe Webhook events", description = "Receives signed payment events from Stripe")
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        if (signature == null || signature.isBlank()) {
            log.warn("⚠️ Received Stripe webhook without Stripe-Signature header");
            return ResponseEntity.status(400).body("Missing signature");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("❌ Stripe signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(400).body("Invalid signature");
        } catch (Exception e) {
            log.error("❌ Unexpected error parsing Stripe webhook: {}", e.getMessage());
            return ResponseEntity.status(400).body("Webhook error: " + e.getMessage());
        }

        log.info("🔔 Stripe event received: {} [{}]", event.getType(), event.getId());

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();

        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                if (dataObjectDeserializer.getObject().isPresent()) {
                    PaymentIntent paymentIntent = (PaymentIntent) dataObjectDeserializer.getObject().get();
                    handlePaymentIntentSucceeded(paymentIntent);
                }
            }
            case "charge.refunded" -> {
                if (dataObjectDeserializer.getObject().isPresent()) {
                    Object obj = dataObjectDeserializer.getObject().get();
                    if (obj instanceof com.stripe.model.Charge charge) {
                        handleChargeRefunded(charge);
                    }
                }
            }
            default -> log.info("ℹ️ Ignored Stripe event type: {}", event.getType());
        }

        return ResponseEntity.ok("OK");
    }

    private void handlePaymentIntentSucceeded(PaymentIntent intent) {
        String tripId = intent.getMetadata() != null ? intent.getMetadata().get("trip_id") : null;
        if (tripId == null || tripId.isBlank()) {
            log.warn("⚠️ PaymentIntent {} succeeded but no trip_id metadata found", intent.getId());
            return;
        }

        Optional<BillingRecord> recordOpt = repository.findByTripId(tripId);
        if (recordOpt.isPresent()) {
            BillingRecord record = recordOpt.get();
            record.setStatus("CHARGED");
            record.setStripePaymentId(intent.getId());
            if (record.getChargedAt() == null) {
                record.setChargedAt(Instant.now());
            }
            repository.save(record);
            log.info("✅ Webhook updated billing for trip {} to CHARGED (PaymentIntent={})", tripId, intent.getId());
        } else {
            log.warn("⚠️ Webhook PaymentIntent succeeded for trip {} but record not found in DB", tripId);
        }
    }

    private void handleChargeRefunded(com.stripe.model.Charge charge) {
        String tripId = charge.getMetadata() != null ? charge.getMetadata().get("trip_id") : null;
        if (tripId == null || tripId.isBlank()) {
            log.warn("⚠️ Charge {} refunded but no trip_id metadata found", charge.getId());
            return;
        }

        Optional<BillingRecord> recordOpt = repository.findByTripId(tripId);
        if (recordOpt.isPresent()) {
            BillingRecord record = recordOpt.get();
            record.setStatus("REFUNDED");
            repository.save(record);
            log.info("↩️ Webhook updated billing for trip {} to REFUNDED", tripId);
        }
    }
}
