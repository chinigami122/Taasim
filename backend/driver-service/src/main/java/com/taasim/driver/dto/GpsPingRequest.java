package com.taasim.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Driver telemetry GPS ping payload")
public class GpsPingRequest {

    @Schema(description = "Driver taxi vehicle identifier", example = "taxi_001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String driverId;

    @Schema(description = "GPS Latitude coordinate", example = "33.5731", requiredMode = Schema.RequiredMode.REQUIRED)
    private double lat;

    @Schema(description = "GPS Longitude coordinate", example = "-7.5898", requiredMode = Schema.RequiredMode.REQUIRED)
    private double lon;

    @Schema(description = "Vehicle speed in km/h", example = "35.0")
    private double speed;

    // Default constructor (required by Jackson JSON parser)
    public GpsPingRequest() {}

    public GpsPingRequest(String driverId, double lat, double lon, double speed) {
        this.driverId = driverId;
        this.lat = lat;
        this.lon = lon;
        this.speed = speed;
    }

    // Getters and Setters (required by Jackson)
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
}