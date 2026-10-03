/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
/**
 * Trip booking request payload with optional exact GPS coordinates and zone fallback
 */
export type TripRequestDto = {
    /**
     * Rider user identifier
     */
    riderId: string;
    /**
     * Pickup origin zone ID (1 to 16)
     */
    originZone?: number;
    /**
     * Dropoff destination zone ID (1 to 16)
     */
    destinationZone?: number;
    /**
     * Exact pickup latitude
     */
    originLat?: number;
    /**
     * Exact pickup longitude
     */
    originLon?: number;
    /**
     * Exact dropoff latitude
     */
    destinationLat?: number;
    /**
     * Exact dropoff longitude
     */
    destinationLon?: number;
};

