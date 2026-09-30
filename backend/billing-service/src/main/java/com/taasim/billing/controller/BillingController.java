package com.taasim.billing.controller;

import com.taasim.billing.model.BillingRecord;
import com.taasim.billing.repository.BillingRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Billing", description = "Fare calculation and trip billing records")
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingRecordRepository repository;

    public BillingController(BillingRecordRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Get paginated billing history for authenticated client")
    @GetMapping("/history")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> myBillingHistory(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageReq = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<BillingRecord> records = repository.findByClientId(userId, pageReq);

        return ResponseEntity.ok(Map.of(
                "records", records.getContent().stream().map(this::toSummaryDto).toList(),
                "totalPages", records.getTotalPages(),
                "totalElements", records.getTotalElements(),
                "page", page));
    }

    @Operation(summary = "Get detailed fare breakdown by Trip ID")
    @GetMapping("/trips/{tripId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'DRIVER', 'ADMIN')")
    public ResponseEntity<?> getBillingByTrip(
            @PathVariable String tripId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        return repository.findByTripId(tripId)
                .map(r -> {
                    // Authorization: only the client or the driver of this trip, or an admin
                    if (userRole != null && !"ADMIN".equalsIgnoreCase(userRole) && userId != null) {
                        if (!userId.equals(r.getClientId()) && !userId.equals(r.getDriverId())) {
                            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body(Map.of("error", "Not your trip"));
                        }
                    }
                    return ResponseEntity.ok(toDetailDto(r));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get billing history for a Driver")
    @GetMapping("/drivers/{driverId}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<List<BillingRecord>> getDriverBilling(@PathVariable String driverId) {
        return ResponseEntity.ok(repository.findByDriverIdOrderByCreatedAtDesc(driverId));
    }

    @Operation(summary = "Get billing history for a Client")
    @GetMapping("/clients/{clientId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<List<BillingRecord>> getClientBilling(@PathVariable String clientId) {
        return ResponseEntity.ok(repository.findByClientIdOrderByCreatedAtDesc(clientId));
    }

    private Map<String, Object> toSummaryDto(BillingRecord r) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("tripId", r.getTripId() != null ? r.getTripId() : "");
        map.put("totalFare", r.getTotalFare() != null ? r.getTotalFare() : 0);
        map.put("currency", r.getCurrency() != null ? r.getCurrency() : "MAD");
        map.put("status", r.getStatus() != null ? r.getStatus() : "");
        map.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
        return map;
    }

    private Map<String, Object> toDetailDto(BillingRecord r) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("tripId", r.getTripId() != null ? r.getTripId() : "");
        map.put("clientId", r.getClientId() != null ? r.getClientId() : "");
        map.put("driverId", r.getDriverId() != null ? r.getDriverId() : "");
        map.put("baseFare", r.getBaseFare() != null ? r.getBaseFare() : 0);
        map.put("distanceFare", r.getDistanceFare() != null ? r.getDistanceFare() : 0);
        map.put("timeFare", r.getTimeFare() != null ? r.getTimeFare() : 0);
        map.put("surge", r.getSurgeMultiplier() != null ? r.getSurgeMultiplier() : 1.0);
        map.put("totalFare", r.getTotalFare() != null ? r.getTotalFare() : 0);
        map.put("commission", r.getCommission() != null ? r.getCommission() : 0);
        map.put("driverPayout", r.getDriverPayout() != null ? r.getDriverPayout() : 0);
        map.put("currency", r.getCurrency() != null ? r.getCurrency() : "MAD");
        map.put("status", r.getStatus() != null ? r.getStatus() : "");
        map.put("distanceKm", r.getDistanceKm() != null ? r.getDistanceKm() : 0);
        map.put("durationMin", r.getDurationMin() != null ? r.getDurationMin() : 0);
        map.put("chargedAt", r.getChargedAt() != null ? r.getChargedAt().toString() : "");
        return map;
    }
}
