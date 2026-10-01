package com.taasim.driver.controller;

import com.taasim.driver.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/drivers")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Drivers", description = "Admin driver management and live tracking endpoints")
public class AdminDriverController {

    private final DriverService driverService;

    public AdminDriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @Operation(summary = "List all drivers with status filtering and pagination")
    @GetMapping
    public ResponseEntity<?> listAll(
            @RequestParam(required = false) String status,     // AVAILABLE | BUSY | OFFLINE
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        List<Map<String, Object>> drivers = driverService.listAllDrivers(status, page, size);
        return ResponseEntity.ok(Map.of(
                "drivers", drivers,
                "count", drivers.size(),
                "filter", status == null ? "all" : status,
                "page", page,
                "size", size
        ));
    }

    @Operation(summary = "Get detailed driver profile with recent trips")
    @GetMapping("/{driverId}")
    public ResponseEntity<?> getDriverDetail(@PathVariable String driverId) {
        Map<String, Object> detail = driverService.getDriverDetail(driverId);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }

    @Operation(summary = "Get live GPS positions of all active drivers")
    @GetMapping("/live-positions")
    public ResponseEntity<?> livePositions() {
        return ResponseEntity.ok(driverService.getAllLivePositions());
    }
}
