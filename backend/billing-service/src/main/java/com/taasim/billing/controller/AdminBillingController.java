package com.taasim.billing.controller;

import com.taasim.billing.repository.BillingRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/billing")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Billing", description = "Admin financial analytics and top driver leaderboards")
public class AdminBillingController {

    private final BillingRecordRepository repo;

    public AdminBillingController(BillingRecordRepository repo) {
        this.repo = repo;
    }

    @Operation(summary = "Get daily revenue, commission, and payout summary")
    @GetMapping("/summary")
    public ResponseEntity<?> summary(@RequestParam(required = false) String date) {
        ZoneId tz = ZoneId.of("Africa/Casablanca");
        LocalDate d = (date == null || date.isBlank()) ? LocalDate.now(tz) : LocalDate.parse(date.trim());
        Instant start = d.atStartOfDay(tz).toInstant();
        Instant end   = d.plusDays(1).atStartOfDay(tz).toInstant();

        BigDecimal revenue    = repo.sumRevenueBetween(start, end);
        BigDecimal commission = repo.sumCommissionBetween(start, end);
        BigDecimal payouts    = repo.sumPayoutsBetween(start, end);
        long completed        = repo.countBetween(start, end);

        return ResponseEntity.ok(Map.of(
                "date", d.toString(),
                "revenue", revenue != null ? revenue : BigDecimal.ZERO,
                "commission", commission != null ? commission : BigDecimal.ZERO,
                "driverPayouts", payouts != null ? payouts : BigDecimal.ZERO,
                "completedTrips", completed,
                "currency", "MAD"
        ));
    }

    @Operation(summary = "Get top drivers by payout leaderboard")
    @GetMapping("/top-drivers")
    public ResponseEntity<?> topDrivers(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String date) {

        ZoneId tz = ZoneId.of("Africa/Casablanca");
        LocalDate d = (date == null || date.isBlank()) ? LocalDate.now(tz) : LocalDate.parse(date.trim());
        Instant start = d.atStartOfDay(tz).toInstant();
        Instant end   = d.plusDays(1).atStartOfDay(tz).toInstant();

        List<Object[]> raw = repo.topDriversByPayoutBetween(limit, start, end);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : raw) {
            String driverId = row[0] != null ? row[0].toString() : "";
            BigDecimal payout = row[1] instanceof BigDecimal bd ? bd : (row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO);
            long trips = row[2] instanceof Number n ? n.longValue() : 0L;
            result.add(Map.of(
                    "driverId", driverId,
                    "payout", payout,
                    "trips", trips
            ));
        }

        return ResponseEntity.ok(result);
    }
}
