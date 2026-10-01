package com.taasim.trip.controller;

import com.taasim.trip.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/trips")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Trips", description = "Admin trip history and operational analytics endpoints")
public class AdminTripController {

    private final TripService tripService;

    public AdminTripController(TripService tripService) {
        this.tripService = tripService;
    }

    @Operation(summary = "List trips with status, date, and limit filters")
    @GetMapping
    public ResponseEntity<?> listTrips(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String date,       // YYYY-MM-DD
            @RequestParam(defaultValue = "50") int limit) {

        return ResponseEntity.ok(tripService.adminListTrips(status, date, limit));
    }

    @Operation(summary = "Get trip operational KPIs and breakdown for a given date")
    @GetMapping("/stats")
    public ResponseEntity<?> stats(@RequestParam(required = false) String date) {
        return ResponseEntity.ok(tripService.tripStatsForDate(date));
    }
}
