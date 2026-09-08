package com.taasim.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trip booking request payload")
public class TripRequestDto {

    @Schema(description = "Rider user identifier", example = "rider_1234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String riderId;

    @Schema(description = "Pickup origin zone ID (1 to 16)", example = "5", minimum = "1", maximum = "16", requiredMode = Schema.RequiredMode.REQUIRED)
    private int originZone;

    @Schema(description = "Dropoff destination zone ID (1 to 16)", example = "12", minimum = "1", maximum = "16", requiredMode = Schema.RequiredMode.REQUIRED)
    private int destinationZone;

    public TripRequestDto() {}

    public String getRiderId() { return riderId; }
    public void setRiderId(String riderId) { this.riderId = riderId; }

    public int getOriginZone() { return originZone; }
    public void setOriginZone(int originZone) { this.originZone = originZone; }

    public int getDestinationZone() { return destinationZone; }
    public void setDestinationZone(int destinationZone) { this.destinationZone = destinationZone; }
}