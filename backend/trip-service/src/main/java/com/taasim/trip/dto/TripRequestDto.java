package com.taasim.trip.dto;

/**
 * JSON body for POST /api/trips/request
 *
 * Example:
 * {
 *   "riderId": "rider_1234",
 *   "originZone": 5,
 *   "destinationZone": 12
 * }
 */
public class TripRequestDto {

    private String riderId;
    private int originZone;
    private int destinationZone;

    public TripRequestDto() {}

    public String getRiderId() { return riderId; }
    public void setRiderId(String riderId) { this.riderId = riderId; }

    public int getOriginZone() { return originZone; }
    public void setOriginZone(int originZone) { this.originZone = originZone; }

    public int getDestinationZone() { return destinationZone; }
    public void setDestinationZone(int destinationZone) { this.destinationZone = destinationZone; }
}