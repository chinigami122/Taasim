package com.taasim.billing.service;

import com.taasim.billing.model.BillingRecord;
import com.taasim.billing.repository.BillingRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final BillingRecordRepository repo;
    private final FareCalculator calculator;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BillingService(BillingRecordRepository repo,
                          FareCalculator calculator,
                          KafkaTemplate<String, Object> kafkaTemplate) {
        this.repo = repo;
        this.calculator = calculator;
        this.kafkaTemplate = kafkaTemplate;
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

        kafkaTemplate.send("billing.completed", tripId, Map.of(
                "trip_id",       tripId,
                "billing_id",    saved.getId().toString(),
                "total_fare",    saved.getTotalFare(),
                "driver_payout", saved.getDriverPayout(),
                "currency",      saved.getCurrency()
        ));

        log.info("💰 Billed trip {} → {} MAD (Driver Payout: {} MAD)",
                tripId, saved.getTotalFare(), saved.getDriverPayout());

        return saved;
    }

    public Optional<BillingRecord> getBillingByTripId(String tripId) {
        return repo.findByTripId(tripId);
    }
}
