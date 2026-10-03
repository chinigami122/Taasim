/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BillingRecord } from '../models/BillingRecord';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class BillingService {
    /**
     * Get detailed fare breakdown by Trip ID
     * @returns any OK
     * @throws ApiError
     */
    public static getBillingByTrip({
        tripId,
        xUserId,
        xUserRole,
    }: {
        tripId: string,
        xUserId?: string,
        xUserRole?: string,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/billing/trips/{tripId}',
            path: {
                'tripId': tripId,
            },
            headers: {
                'X-User-Id': xUserId,
                'X-User-Role': xUserRole,
            },
        });
    }
    /**
     * Get paginated billing history for authenticated client
     * @returns any OK
     * @throws ApiError
     */
    public static myBillingHistory({
        xUserId,
        page,
        size = 20,
    }: {
        xUserId: string,
        page?: number,
        size?: number,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/billing/history',
            headers: {
                'X-User-Id': xUserId,
            },
            query: {
                'page': page,
                'size': size,
            },
        });
    }
    /**
     * Get billing history for a Driver
     * @returns BillingRecord OK
     * @throws ApiError
     */
    public static getDriverBilling({
        driverId,
    }: {
        driverId: string,
    }): CancelablePromise<Array<BillingRecord>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/billing/drivers/{driverId}',
            path: {
                'driverId': driverId,
            },
        });
    }
    /**
     * Get billing history for a Client
     * @returns BillingRecord OK
     * @throws ApiError
     */
    public static getClientBilling({
        clientId,
    }: {
        clientId: string,
    }): CancelablePromise<Array<BillingRecord>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/billing/clients/{clientId}',
            path: {
                'clientId': clientId,
            },
        });
    }
}
