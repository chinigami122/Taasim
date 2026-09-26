package com.taasim.billing.repository;

import com.taasim.billing.model.BillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingRecordRepository extends JpaRepository<BillingRecord, UUID> {
    Optional<BillingRecord> findByTripId(String tripId);
    List<BillingRecord> findByDriverIdOrderByCreatedAtDesc(String driverId);
    List<BillingRecord> findByClientIdOrderByCreatedAtDesc(String clientId);
    boolean existsByTripId(String tripId);
}
