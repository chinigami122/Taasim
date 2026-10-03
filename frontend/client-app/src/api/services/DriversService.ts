/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { GpsPingRequest } from '../models/GpsPingRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class DriversService {
    /**
     * Start active ride
     * Driver picks up rider and marks trip as STARTED.
     * @returns any Ride started
     * @throws ApiError
     */
    public static startRide({
        driverId,
    }: {
        /**
         * Driver identifier
         */
        driverId: string,
    }): CancelablePromise<Record<string, Record<string, any>>> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/api/drivers/trips/{driverId}/start',
            path: {
                'driverId': driverId,
            },
            errors: {
                400: `No active trip for this driver`,
                401: `Missing or invalid JWT`,
                403: `Insufficient role (requires DRIVER)`,
            },
        });
    }
    /**
     * Reject matched trip
     * Driver rejects an assigned trip match.
     * @returns any Trip rejected
     * @throws ApiError
     */
    public static rejectTrip({
        driverId,
    }: {
        /**
         * Driver identifier
         */
        driverId: string,
    }): CancelablePromise<Record<string, Record<string, any>>> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/api/drivers/trips/{driverId}/reject',
            path: {
                'driverId': driverId,
            },
            errors: {
                400: `No pending trip for this driver`,
                401: `Missing or invalid JWT`,
                403: `Insufficient role (requires DRIVER)`,
            },
        });
    }
    /**
     * Complete active ride
     * Driver drops off rider and marks trip as COMPLETED.
     * @returns any Ride completed
     * @throws ApiError
     */
    public static completeRide({
        driverId,
    }: {
        /**
         * Driver identifier
         */
        driverId: string,
    }): CancelablePromise<Record<string, Record<string, any>>> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/api/drivers/trips/{driverId}/complete',
            path: {
                'driverId': driverId,
            },
            errors: {
                400: `No active trip for this driver`,
                401: `Missing or invalid JWT`,
                403: `Insufficient role (requires DRIVER)`,
            },
        });
    }
    /**
     * Accept matched trip
     * Driver accepts an assigned trip match.
     * @returns any Trip accepted
     * @throws ApiError
     */
    public static acceptTrip({
        driverId,
    }: {
        /**
         * Driver identifier
         */
        driverId: string,
    }): CancelablePromise<Record<string, Record<string, any>>> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/api/drivers/trips/{driverId}/accept',
            path: {
                'driverId': driverId,
            },
            errors: {
                400: `No pending trip for this driver`,
                401: `Missing or invalid JWT`,
                403: `Insufficient role (requires DRIVER)`,
            },
        });
    }
    /**
     * Publish driver GPS ping
     * Receives telemetry coordinates from a driver and writes them to Cassandra.
     * @returns any Position recorded
     * @throws ApiError
     */
    public static receiveLocation({
        requestBody,
        xUserId,
    }: {
        requestBody: GpsPingRequest,
        xUserId?: string,
    }): CancelablePromise<Record<string, Record<string, any>>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/drivers/location',
            headers: {
                'X-User-Id': xUserId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Missing or invalid JWT`,
                403: `Insufficient role (requires DRIVER)`,
            },
        });
    }
    /**
     * Get driver trip history from Cassandra
     * Retrieves past trips for the authenticated driver.
     * @returns any Trip history returned
     * @throws ApiError
     */
    public static myTrips({
        xUserId,
        xUserRole,
        driverId,
        limit = 20,
    }: {
        xUserId?: string,
        xUserRole?: string,
        driverId?: string,
        limit?: number,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/drivers/trips/history',
            headers: {
                'X-User-Id': xUserId,
                'X-User-Role': xUserRole,
            },
            query: {
                'driverId': driverId,
                'limit': limit,
            },
            errors: {
                401: `Missing or invalid JWT`,
                403: `Insufficient role or accessing another driver's data`,
            },
        });
    }
}
