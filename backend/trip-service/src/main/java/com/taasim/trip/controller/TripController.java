package com.taasim.trip.controller;

import com.taasim.trip.dto.TripRequestDto;
import com.taasim.trip.model.Trip;
import com.taasim.trip.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Trips", description = "Trip request and lifecycle management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    /**
     * POST /api/trips/request
     *
     * Body: { "riderId": "rider_1234", "originZone": 5, "destinationZone": 12 }
     * Response: { "tripId": "uuid...", "status": "REQUESTED" }
     */
    @Operation(
        summary = "Request a new trip",
        description = "Creates a REQUESTED trip and publishes it to Kafka for driver matching."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trip created and submitted for matching"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "Insufficient role (requires CLIENT)")
    })
    @PreAuthorize("hasRole('CLIENT')")
    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> requestTrip(@RequestBody TripRequestDto request) {
        Trip trip = tripService.createTrip(request);

        return ResponseEntity.ok(Map.of(
                "tripId", trip.getTripId(),
                "status", trip.getStatus(),
                "originZone", trip.getOriginZone(),
                "destinationZone", trip.getDestZone(),
                "message", "Trip request submitted"
        ));
    }

    /**
     * GET /api/trips/{tripId}
     * Returns current trip status.
     */
    @Operation(
        summary = "Get current trip status",
        description = "Retrieves real-time trip status, matched driver, and estimated time of arrival."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trip found"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    @PreAuthorize("hasAnyRole('CLIENT', 'DRIVER')")
    @GetMapping("/{tripId}")
    public ResponseEntity<?> getTrip(
            @Parameter(description = "Trip unique identifier (UUID)", example = "4e3415c1-3fbe-497b-8bb5-5d462fa9c207")
            @PathVariable String tripId) {
        Trip trip = tripService.getTripById(tripId);
        if (trip == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "tripId", trip.getTripId(),
                "status", trip.getStatus(),
                "driverId", trip.getTaxiId() != null ? trip.getTaxiId() : "",
                "etaSeconds", trip.getEtaSeconds() != null ? trip.getEtaSeconds() : 0,
                "originZone", trip.getOriginZone(),
                "destinationZone", trip.getDestZone()
        ));
    }
}