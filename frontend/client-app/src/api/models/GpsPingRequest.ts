/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
/**
 * Driver telemetry GPS ping payload
 */
export type GpsPingRequest = {
    /**
     * Driver taxi vehicle identifier
     */
    driverId: string;
    /**
     * GPS Latitude coordinate
     */
    lat: number;
    /**
     * GPS Longitude coordinate
     */
    lon: number;
    /**
     * Vehicle speed in km/h
     */
    speed?: number;
};

