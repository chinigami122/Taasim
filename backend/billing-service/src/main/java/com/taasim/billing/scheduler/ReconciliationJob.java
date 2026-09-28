package com.taasim.billing.scheduler;

import com.taasim.billing.model.BillingRecord;
import com.taasim.billing.repository.BillingRecordRepository;
import com.taasim.billing.service.BillingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final BillingRecordRepository repo;
    private final BillingService billingService;

    public ReconciliationJob(BillingRecordRepository repo, BillingService billingService) {
        this.repo = repo;
        this.billingService = billingService;
    }

    /**
     * Every hour (configurable), find unbilled trips older than 5 minutes with attempts < 5 and retry.
     * Protected by Stripe idempotency key.
     */
    @Scheduled(cron = "${billing.reconciliation.cron:0 0 * * * *}")
    public void retryUnbilled() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(5));
        List<BillingRecord> pending = repo.findByStatusAndCreatedAtBeforeAndChargeAttemptsLessThan("CALCULATED", cutoff, 5);

        if (pending.isEmpty()) {
            return;
        }

        log.info("🔄 Reconciliation job found {} pending CALCULATED records older than 5m to retry", pending.size());
        for (BillingRecord r : pending) {
            try {
                billingService.retryCharge(r);
            } catch (Exception e) {
                log.error("❌ Reconciliation failed for trip {}: {}", r.getTripId(), e.getMessage());
            }
        }
    }
}
