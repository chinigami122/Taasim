package com.taasim.driver.controller;

import com.taasim.driver.dto.GpsPingRequest;
import com.taasim.driver.model.VehiclePosition;
import com.taasim.driver.service.DriverService;
import com.taasim.driver.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for driver-related endpoints.
 *
 * Slice 1: POST /api/drivers/location only.
 * More endpoints will be added in later slices.
 */
@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final LocationService locationService;
    private final DriverService driverService;

    public DriverController(LocationService locationService, DriverService driverService) {
        this.locationService = locationService;
        this.driverService = driverService;
    }

    /**
     * Receive a GPS ping from a driver.
     *
     * Request body:
     * {
     *   "driverId": "taxi_001",
     *   "lat": 33.5731,
     *   "lon": -7.5898,
     *   "speed": 35.0
     * }
     *
     * Response:
     * {
     *   "status": "ok",
     *   "driverId": "taxi_001",
     *   "zoneId": 5,
     *   "message": "Position saved to Cassandra"
     * }
     */
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
    /** PUT /api/drivers/trips/{driverId}/accept */
    @PutMapping("/trips/{driverId}/accept")
    public ResponseEntity<Map<String, Object>> acceptTrip(@PathVariable String driverId) {
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
    /** PUT /api/drivers/trips/{driverId}/reject */
    @PutMapping("/trips/{driverId}/reject")
    public ResponseEntity<Map<String, Object>> rejectTrip(@PathVariable String driverId) {
        boolean rejected = driverService.rejectTrip(driverId);
        if (!rejected) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "No pending trip for driver " + driverId
            ));
        }
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Trip rejected"));
    }

    /** PUT /api/drivers/trips/{driverId}/start */
    @PutMapping("/trips/{driverId}/start")
    public ResponseEntity<Map<String, Object>> startRide(@PathVariable String driverId) {
        boolean started = driverService.startRide(driverId);
        if (!started) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "No active trip for driver " + driverId
            ));
        }
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Ride started"));
    }

    /** PUT /api/drivers/trips/{driverId}/complete */
    @PutMapping("/trips/{driverId}/complete")
    public ResponseEntity<Map<String, Object>> completeRide(@PathVariable String driverId) {
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