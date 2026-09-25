package com.taasim.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trip booking request payload with optional exact GPS coordinates and zone fallback")
public class TripRequestDto {

    @Schema(description = "Rider user identifier", example = "rider_1234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String riderId;

    @Schema(description = "Pickup origin zone ID (1 to 16)", example = "5", minimum = "1", maximum = "16")
    private Integer originZone;

    @Schema(description = "Dropoff destination zone ID (1 to 16)", example = "12", minimum = "1", maximum = "16")
    private Integer destinationZone;

    @Schema(description = "Exact pickup latitude", example = "33.5731")
    private Double originLat;

    @Schema(description = "Exact pickup longitude", example = "-7.5898")
    private Double originLon;

    @Schema(description = "Exact dropoff latitude", example = "33.5900")
    private Double destinationLat;

    @Schema(description = "Exact dropoff longitude", example = "-7.6100")
    private Double destinationLon;

    public TripRequestDto() {}

    public String getRiderId() { return riderId; }
    public void setRiderId(String riderId) { this.riderId = riderId; }

    public Integer getOriginZone() { return originZone; }
    public void setOriginZone(Integer originZone) { this.originZone = originZone; }

    public Integer getDestinationZone() { return destinationZone; }
    public void setDestinationZone(Integer destinationZone) { this.destinationZone = destinationZone; }

    public Double getOriginLat() { return originLat; }
    public void setOriginLat(Double originLat) { this.originLat = originLat; }

    public Double getOriginLon() { return originLon; }
    public void setOriginLon(Double originLon) { this.originLon = originLon; }

    public Double getDestinationLat() { return destinationLat; }
    public void setDestinationLat(Double destinationLat) { this.destinationLat = destinationLat; }

    public Double getDestinationLon() { return destinationLon; }
    public void setDestinationLon(Double destinationLon) { this.destinationLon = destinationLon; }
}