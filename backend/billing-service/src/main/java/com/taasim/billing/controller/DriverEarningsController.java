package com.taasim.billing.controller;

import com.taasim.billing.repository.BillingRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Driver Earnings", description = "Driver payout and earnings summary")
@RestController
@RequestMapping({ "/api/drivers/earnings", "/api/billing/drivers/earnings" })
public class DriverEarningsController {

    private final BillingRecordRepository repo;

    public DriverEarningsController(BillingRecordRepository repo) {
        this.repo = repo;
    }

    @Operation(summary = "Get driver earnings summary (today, this week, this month)")
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<?> summary(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "driverId", required = false) String queryDriverId) {

        // Tenant isolation: DRIVER can only view their own earnings
        if (queryDriverId != null && !queryDriverId.isBlank() && userId != null && !userId.equals(queryDriverId)) {
            if (!"ADMIN".equals(role)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "status", "error",
                        "message", "Access denied: cannot view another driver's earnings"));
            }
        }

        String effectiveDriverId = (queryDriverId != null && !queryDriverId.isBlank()) ? queryDriverId : userId;
        if (effectiveDriverId == null || effectiveDriverId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Driver ID missing"));
        }

        ZoneId tz = ZoneId.of("Africa/Casablanca");
        LocalDate todayCasablanca = LocalDate.now(tz);

        Instant startOfDay = todayCasablanca.atStartOfDay(tz).toInstant();
        Instant startOfWeek = todayCasablanca
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(tz).toInstant();
        Instant startOfMonth = todayCasablanca
                .withDayOfMonth(1)
                .atStartOfDay(tz).toInstant();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("today", Map.of(
                "earnings", repo.sumPayoutSince(effectiveDriverId, startOfDay),
                "trips", repo.countCompletedSince(effectiveDriverId, startOfDay)));
        response.put("thisWeek", Map.of(
                "earnings", repo.sumPayoutSince(effectiveDriverId, startOfWeek),
                "trips", repo.countCompletedSince(effectiveDriverId, startOfWeek)));
        response.put("thisMonth", Map.of(
                "earnings", repo.sumPayoutSince(effectiveDriverId, startOfMonth),
                "trips", repo.countCompletedSince(effectiveDriverId, startOfMonth)));
        response.put("currency", "MAD");

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get paginated list of driver trip earnings")
    @GetMapping("/trips")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<?> tripEarnings(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "driverId", required = false) String queryDriverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Tenant isolation: DRIVER can only view their own trip earnings
        if (queryDriverId != null && !queryDriverId.isBlank() && userId != null && !userId.equals(queryDriverId)) {
            if (!"ADMIN".equals(role)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "status", "error",
                        "message", "Access denied: cannot view another driver's trip earnings"));
            }
        }

        String effectiveDriverId = (queryDriverId != null && !queryDriverId.isBlank()) ? queryDriverId : userId;
        if (effectiveDriverId == null || effectiveDriverId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Driver ID missing"));
        }

        var pageReq = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var records = repo.findByDriverId(effectiveDriverId, pageReq);

        var tripsList = records.getContent().stream().map(r -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tripId", r.getTripId() != null ? r.getTripId() : "");
            item.put("totalFare", r.getTotalFare() != null ? r.getTotalFare() : 0);
            item.put("commission", r.getCommission() != null ? r.getCommission() : 0);
            item.put("payout", r.getDriverPayout() != null ? r.getDriverPayout() : 0);
            item.put("distanceKm", r.getDistanceKm() != null ? r.getDistanceKm() : 0);
            item.put("durationMin", r.getDurationMin() != null ? r.getDurationMin() : 0);
            item.put("status", r.getStatus() != null ? r.getStatus() : "");
            item.put("chargedAt", r.getChargedAt() != null ? r.getChargedAt().toString()
                    : (r.getCreatedAt() != null ? r.getCreatedAt().toString() : ""));
            return item;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("trips", tripsList);
        response.put("totalPages", records.getTotalPages());
        response.put("totalElements", records.getTotalElements());
        response.put("page", page);
        response.put("size", size);

        return ResponseEntity.ok(response);
    }
}
