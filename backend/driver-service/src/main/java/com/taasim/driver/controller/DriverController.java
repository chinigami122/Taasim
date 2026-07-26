package com.taasim.driver.controller;

import com.taasim.driver.dto.GpsPingRequest;
import com.taasim.driver.model.VehiclePosition;
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

    public DriverController(LocationService locationService) {
        this.locationService = locationService;
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
}