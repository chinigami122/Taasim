package com.taasim.billing.service;

import com.taasim.billing.model.BillingRecord;
import com.taasim.billing.repository.BillingRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final BillingRecordRepository repo;
    private final FareCalculator calculator;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StripeChargeService stripeChargeService;
    private final ClientLookupService clientLookupService;

    public BillingService(BillingRecordRepository repo,
                          FareCalculator calculator,
                          KafkaTemplate<String, Object> kafkaTemplate,
                          StripeChargeService stripeChargeService,
                          ClientLookupService clientLookupService) {
        this.repo = repo;
        this.calculator = calculator;
        this.kafkaTemplate = kafkaTemplate;
        this.stripeChargeService = stripeChargeService;
        this.clientLookupService = clientLookupService;
    }

    @Transactional
    public BillingRecord createBillingFor(String tripId,
                                          String driverId,
                                          String clientId,
                                          double distanceKm,
                                          double durationMin,
                                          double surge) {
        // Idempotency: don't double-bill if the consumer replays
        if (repo.existsByTripId(tripId)) {
            log.info("↩︎ Billing already exists for trip {}, skipping duplicate creation", tripId);
            return repo.findByTripId(tripId).orElseThrow();
        }

        var fare = calculator.calculate(distanceKm, durationMin, surge);

        BillingRecord r = new BillingRecord();
        r.setTripId(tripId);
        r.setDriverId(driverId);
        r.setClientId(clientId != null && !clientId.isBlank() ? clientId : "unknown");
        r.setDistanceKm(BigDecimal.valueOf(distanceKm));
        r.setDurationMin(BigDecimal.valueOf(durationMin));
        r.setBaseFare(fare.baseFare());
        r.setDistanceFare(fare.distanceFare());
        r.setTimeFare(fare.timeFare());
        r.setSurgeMultiplier(fare.surgeMultiplier());
        r.setTotalFare(fare.totalFare());
        r.setCommission(fare.commission());
        r.setDriverPayout(fare.driverPayout());
        r.setStatus("CALCULATED");

        BillingRecord saved = repo.save(r);

        // Slice 16 — attempt Stripe charge (non-blocking, never rollback billing on failure)
        tryCharge(saved);

        kafkaTemplate.send("billing.completed", tripId, Map.of(
                "trip_id",       tripId,
                "billing_id",    saved.getId().toString(),
                "total_fare",    saved.getTotalFare(),
                "driver_payout", saved.getDriverPayout(),
                "currency",      saved.getCurrency(),
                "status",        saved.getStatus()
        ));

        log.info("💰 Billed trip {} → {} MAD (Driver Payout: {} MAD, Status: {})",
                tripId, saved.getTotalFare(), saved.getDriverPayout(), saved.getStatus());

        return saved;
    }

    private void tryCharge(BillingRecord record) {
        if (!"CALCULATED".equals(record.getStatus())) {
            return;
        }
        if (stripeChargeService == null || !stripeChargeService.isConfigured()) {
            log.warn("⚠️ Stripe not configured — leaving billing {} as CALCULATED", record.getTripId());
            return;
        }
        String stripeCustomerId = clientLookupService != null
                ? clientLookupService.getStripeCustomerId(record.getClientId())
                : null;

        if (stripeCustomerId == null || stripeCustomerId.isBlank()) {
            log.warn("⚠️ No Stripe customer for client {} — leaving billing {} as CALCULATED",
                    record.getClientId(), record.getTripId());
            return;
        }

        try {
            String pm = "pm_card_visa"; // Stripe test PaymentMethod
            long amountMinor = record.getTotalFare().movePointRight(2).longValueExact();
            String pi = stripeChargeService.chargeCustomer(
                    stripeCustomerId,
                    pm,
                    amountMinor,
                    "TaaSim ride " + record.getTripId(),
                    record.getTripId()
            );
            record.setStatus("CHARGED");
            record.setStripePaymentId(pi);
            record.setChargedAt(Instant.now());
            repo.save(record);
            log.info("💳 Stripe charge OK: {} for trip {}", pi, record.getTripId());
        } catch (Exception e) {
            log.error("❌ Stripe charge failed for trip {}: {}", record.getTripId(), e.getMessage());
            // Leave status as CALCULATED — retry will be handled in Slice 16.5 / 20.5
        }
    }

    public Optional<BillingRecord> getBillingByTripId(String tripId) {
        return repo.findByTripId(tripId);
    }
}
