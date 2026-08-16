package com.taasim.trip.controller;

import com.taasim.trip.dto.TripRequestDto;
import com.taasim.trip.model.Trip;
import com.taasim.trip.service.TripService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    @GetMapping("/{tripId}")
    public ResponseEntity<?> getTrip(@PathVariable String tripId) {
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