package com.taasim.geospatial.controller;

import com.taasim.geospatial.service.ProximityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Geospatial", description = "Driver location indexing and proximity searches")
@RestController
@RequestMapping("/internal")
public class GeospatialController {

    private final ProximityService proximityService;

    public GeospatialController(ProximityService proximityService) {
        this.proximityService = proximityService;
    }

    @Operation(summary = "Update driver position in Redis GEO index")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Position updated successfully")
    })
    @PutMapping("/drivers/{driverId}/position")
    public ResponseEntity<Map<String, Object>> updatePosition(
            @Parameter(description = "Driver identifier", example = "taxi_001")
            @PathVariable String driverId,
            @Parameter(description = "Latitude coordinate", example = "33.5731")
            @RequestParam double lat,
            @Parameter(description = "Longitude coordinate", example = "-7.5898")
            @RequestParam double lon) {
        proximityService.updatePosition(driverId, lat, lon);
        return ResponseEntity.ok(Map.of("status", "ok", "driverId", driverId));
    }

    @Operation(summary = "Find nearby available drivers within radius")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of nearby drivers sorted by distance")
    })
    @GetMapping("/drivers/nearby")
    public ResponseEntity<List<Map<String, Object>>> findNearby(
            @Parameter(description = "Origin Latitude", example = "33.5731")
            @RequestParam double lat,
            @Parameter(description = "Origin Longitude", example = "-7.5898")
            @RequestParam double lon,
            @Parameter(description = "Search radius in meters", example = "2000")
            @RequestParam(defaultValue = "2000") double radius) {
        return ResponseEntity.ok(proximityService.findNearby(lat, lon, radius));
    }

    @Operation(summary = "Remove driver from available GEO index")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver removed from index")
    })
    @DeleteMapping("/drivers/{driverId}/position")
    public ResponseEntity<Map<String, Object>> removeDriver(
            @Parameter(description = "Driver identifier", example = "taxi_001")
            @PathVariable String driverId) {
        proximityService.removeDriver(driverId);
        return ResponseEntity.ok(Map.of("status", "removed", "driverId", driverId));
    }
}
