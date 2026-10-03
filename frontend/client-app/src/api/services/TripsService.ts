/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { TripRequestDto } from '../models/TripRequestDto';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class TripsService {
    /**
     * Request a new trip
     * Creates a REQUESTED trip and publishes it to Kafka for driver matching.
     * @returns any Trip created and submitted for matching
     * @throws ApiError
     */
    public static requestTrip({
        requestBody,
        xUserId,
    }: {
        requestBody: TripRequestDto,
        xUserId?: string,
    }): CancelablePromise<Record<string, Record<string, any>>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/trips/request',
            headers: {
                'X-User-Id': xUserId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Invalid request payload`,
                401: `Missing or invalid JWT`,
                403: `Insufficient role (requires CLIENT)`,
            },
        });
    }
    /**
     * Get current trip status
     * Retrieves real-time trip status, matched driver, and estimated time of arrival.
     * @returns any Trip found
     * @throws ApiError
     */
    public static getTrip({
        tripId,
    }: {
        /**
         * Trip unique identifier (UUID)
         */
        tripId: string,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/trips/{tripId}',
            path: {
                'tripId': tripId,
            },
            errors: {
                401: `Missing or invalid JWT`,
                404: `Trip not found`,
            },
        });
    }
    /**
     * List trips for the authenticated user
     * @returns any OK
     * @throws ApiError
     */
    public static getMyTrips({
        xUserId,
        limit = 20,
        fromDate,
    }: {
        xUserId?: string,
        limit?: number,
        fromDate?: string,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/trips/history',
            headers: {
                'X-User-Id': xUserId,
            },
            query: {
                'limit': limit,
                'fromDate': fromDate,
            },
        });
    }
}
