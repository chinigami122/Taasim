package com.taasim.billing.repository;

import com.taasim.billing.model.BillingRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface BillingRecordRepository extends JpaRepository<BillingRecord, UUID> {
    Optional<BillingRecord> findByTripId(String tripId);
    List<BillingRecord> findByDriverIdOrderByCreatedAtDesc(String driverId);
    List<BillingRecord> findByClientIdOrderByCreatedAtDesc(String clientId);
    Page<BillingRecord> findByClientId(String clientId, Pageable pageable);
    Page<BillingRecord> findByDriverId(String driverId, Pageable pageable);
    boolean existsByTripId(String tripId);
    List<BillingRecord> findByStatusAndCreatedAtBeforeAndChargeAttemptsLessThan(String status, Instant createdAt, int chargeAttempts);

    @Query("""
        SELECT COALESCE(SUM(b.driverPayout), 0) FROM BillingRecord b
        WHERE b.driverId = :driverId 
          AND (b.status = 'CHARGED' OR b.status = 'CALCULATED') 
          AND COALESCE(b.chargedAt, b.createdAt) >= :since
        """)
    BigDecimal sumPayoutSince(@Param("driverId") String driverId, @Param("since") Instant since);

    @Query("""
        SELECT COUNT(b) FROM BillingRecord b
        WHERE b.driverId = :driverId 
          AND (b.status = 'CHARGED' OR b.status = 'CALCULATED') 
          AND COALESCE(b.chargedAt, b.createdAt) >= :since
        """)
    long countCompletedSince(@Param("driverId") String driverId, @Param("since") Instant since);

    @Query("""
        SELECT COALESCE(SUM(b.totalFare), 0) FROM BillingRecord b
        WHERE (b.status = 'CHARGED' OR b.status = 'CALCULATED')
          AND COALESCE(b.chargedAt, b.createdAt) >= :start
          AND COALESCE(b.chargedAt, b.createdAt) < :end
        """)
    BigDecimal sumRevenueBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
        SELECT COALESCE(SUM(b.commission), 0) FROM BillingRecord b
        WHERE (b.status = 'CHARGED' OR b.status = 'CALCULATED')
          AND COALESCE(b.chargedAt, b.createdAt) >= :start
          AND COALESCE(b.chargedAt, b.createdAt) < :end
        """)
    BigDecimal sumCommissionBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
        SELECT COALESCE(SUM(b.driverPayout), 0) FROM BillingRecord b
        WHERE (b.status = 'CHARGED' OR b.status = 'CALCULATED')
          AND COALESCE(b.chargedAt, b.createdAt) >= :start
          AND COALESCE(b.chargedAt, b.createdAt) < :end
        """)
    BigDecimal sumPayoutsBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
        SELECT COUNT(b) FROM BillingRecord b
        WHERE (b.status = 'CHARGED' OR b.status = 'CALCULATED')
          AND COALESCE(b.chargedAt, b.createdAt) >= :start
          AND COALESCE(b.chargedAt, b.createdAt) < :end
        """)
    long countBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query(value = """
        SELECT driver_id, SUM(driver_payout) AS payout, COUNT(*) AS trips
        FROM billing_records
        WHERE (status = 'CHARGED' OR status = 'CALCULATED')
          AND COALESCE(charged_at, created_at) >= :start
          AND COALESCE(charged_at, created_at) < :end
        GROUP BY driver_id
        ORDER BY payout DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> topDriversByPayoutBetween(@Param("limit") int limit, @Param("start") Instant start, @Param("end") Instant end);
}


