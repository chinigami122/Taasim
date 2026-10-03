/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class AdminTripsService {
    /**
     * List trips with status, date, and limit filters
     * @returns any OK
     * @throws ApiError
     */
    public static listTrips({
        status,
        date,
        limit = 50,
    }: {
        status?: string,
        date?: string,
        limit?: number,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/trips',
            query: {
                'status': status,
                'date': date,
                'limit': limit,
            },
        });
    }
    /**
     * Get trip operational KPIs and breakdown for a given date
     * @returns any OK
     * @throws ApiError
     */
    public static stats({
        date,
    }: {
        date?: string,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/trips/stats',
            query: {
                'date': date,
            },
        });
    }
}
