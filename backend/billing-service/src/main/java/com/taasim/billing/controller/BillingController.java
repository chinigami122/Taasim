package com.taasim.billing.controller;

import com.taasim.billing.model.BillingRecord;
import com.taasim.billing.repository.BillingRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Billing", description = "Fare calculation and trip billing records")
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingRecordRepository repository;

    public BillingController(BillingRecordRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Get billing record by Trip ID")
    @GetMapping("/trips/{tripId}")
    public ResponseEntity<BillingRecord> getBillingByTrip(@PathVariable String tripId) {
        return repository.findByTripId(tripId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get billing history for a Driver")
    @GetMapping("/drivers/{driverId}")
    public ResponseEntity<List<BillingRecord>> getDriverBilling(@PathVariable String driverId) {
        return ResponseEntity.ok(repository.findByDriverIdOrderByCreatedAtDesc(driverId));
    }

    @Operation(summary = "Get billing history for a Client")
    @GetMapping("/clients/{clientId}")
    public ResponseEntity<List<BillingRecord>> getClientBilling(@PathVariable String clientId) {
        return ResponseEntity.ok(repository.findByClientIdOrderByCreatedAtDesc(clientId));
    }
}
