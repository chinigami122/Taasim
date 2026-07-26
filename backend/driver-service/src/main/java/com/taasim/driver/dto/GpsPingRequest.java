package com.taasim.driver.dto;

/**
 * JSON body for POST /api/drivers/location
 *
 * Example:
 * {
 *   "driverId": "taxi_001",
 *   "lat": 33.5731,
 *   "lon": -7.5898,
 *   "speed": 35.0
 * }
 */
public class GpsPingRequest {

    private String driverId;
    private double lat;
    private double lon;
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