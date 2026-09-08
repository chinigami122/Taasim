package com.taasim.driver.controller;

import com.taasim.driver.dto.GpsPingRequest;
import com.taasim.driver.model.VehiclePosition;
import com.taasim.driver.service.DriverService;
import com.taasim.driver.service.LocationService;
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

/**
 * REST controller for driver-related endpoints.
 */
@Tag(name = "Drivers", description = "Driver telemetry and trip lifecycle management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final LocationService locationService;
    private final DriverService driverService;

    public DriverController(LocationService locationService, DriverService driverService) {
        this.locationService = locationService;
        this.driverService = driverService;
    }

    @Operation(summary = "Publish driver GPS ping", description = "Receives telemetry coordinates from a driver and writes them to Cassandra.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Position recorded"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "Insufficient role (requires DRIVER)")
    })
    @PreAuthorize("hasRole('DRIVER')")
    @PostMapping("/location")
    public ResponseEntity<Map<String, Object>> receiveLocation(
            @RequestBody GpsPingRequest request) {

        VehiclePosition saved = locationService.processGpsPing(request);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "driverId", saved.getTaxiId(),
                "zoneId", saved.getZoneId(),
                "message", "Position saved to Cassandra"
        ));
    }

    @Operation(summary = "Accept matched trip", description = "Driver accepts an assigned trip match.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trip accepted"),
        @ApiResponse(responseCode = "400", description = "No pending trip for this driver"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "Insufficient role (requires DRIVER)")
    })
    @PreAuthorize("hasRole('DRIVER')")
    @PutMapping("/trips/{driverId}/accept")
    public ResponseEntity<Map<String, Object>> acceptTrip(
            @Parameter(description = "Driver identifier", example = "taxi_001") @PathVariable String driverId) {
        boolean accepted = driverService.acceptTrip(driverId);
        if (!accepted) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "No pending trip for driver " + driverId
            ));
        }
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Trip accepted",
                "tripId", driverService.getActiveTrip(driverId)
        ));
    }

    @Operation(summary = "Reject matched trip", description = "Driver rejects an assigned trip match.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trip rejected"),
        @ApiResponse(responseCode = "400", description = "No pending trip for this driver"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "Insufficient role (requires DRIVER)")
    })
    @PreAuthorize("hasRole('DRIVER')")
    @PutMapping("/trips/{driverId}/reject")
    public ResponseEntity<Map<String, Object>> rejectTrip(
            @Parameter(description = "Driver identifier", example = "taxi_001") @PathVariable String driverId) {
        boolean rejected = driverService.rejectTrip(driverId);
        if (!rejected) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "No pending trip for driver " + driverId
            ));
        }
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Trip rejected"));
    }

    @Operation(summary = "Start active ride", description = "Driver picks up rider and marks trip as STARTED.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ride started"),
        @ApiResponse(responseCode = "400", description = "No active trip for this driver"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "Insufficient role (requires DRIVER)")
    })
    @PreAuthorize("hasRole('DRIVER')")
    @PutMapping("/trips/{driverId}/start")
    public ResponseEntity<Map<String, Object>> startRide(
            @Parameter(description = "Driver identifier", example = "taxi_001") @PathVariable String driverId) {
        boolean started = driverService.startRide(driverId);
        if (!started) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "No active trip for driver " + driverId
            ));
        }
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Ride started"));
    }

    @Operation(summary = "Complete active ride", description = "Driver drops off rider and marks trip as COMPLETED.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ride completed"),
        @ApiResponse(responseCode = "400", description = "No active trip for this driver"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "Insufficient role (requires DRIVER)")
    })
    @PreAuthorize("hasRole('DRIVER')")
    @PutMapping("/trips/{driverId}/complete")
    public ResponseEntity<Map<String, Object>> completeRide(
            @Parameter(description = "Driver identifier", example = "taxi_001") @PathVariable String driverId) {
        boolean completed = driverService.completeRide(driverId);
        if (!completed) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "No active trip for driver " + driverId
            ));
        }
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Ride completed"));
    }
}