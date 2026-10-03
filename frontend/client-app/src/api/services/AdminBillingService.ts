/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class AdminBillingService {
    /**
     * Get top drivers by payout leaderboard
     * @returns any OK
     * @throws ApiError
     */
    public static topDrivers({
        limit = 10,
        date,
    }: {
        limit?: number,
        date?: string,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/billing/top-drivers',
            query: {
                'limit': limit,
                'date': date,
            },
        });
    }
    /**
     * Get daily revenue, commission, and payout summary
     * @returns any OK
     * @throws ApiError
     */
    public static summary2({
        date,
    }: {
        date?: string,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/billing/summary',
            query: {
                'date': date,
            },
        });
    }
}
