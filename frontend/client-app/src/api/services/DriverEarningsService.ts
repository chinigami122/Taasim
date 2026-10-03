/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class DriverEarningsService {
    /**
     * Get paginated list of driver trip earnings
     * @returns any OK
     * @throws ApiError
     */
    public static tripEarnings({
        xUserId,
        xUserRole,
        driverId,
        page,
        size = 20,
    }: {
        xUserId?: string,
        xUserRole?: string,
        driverId?: string,
        page?: number,
        size?: number,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/drivers/earnings/trips',
            headers: {
                'X-User-Id': xUserId,
                'X-User-Role': xUserRole,
            },
            query: {
                'driverId': driverId,
                'page': page,
                'size': size,
            },
        });
    }
    /**
     * Get paginated list of driver trip earnings
     * @returns any OK
     * @throws ApiError
     */
    public static tripEarnings1({
        xUserId,
        xUserRole,
        driverId,
        page,
        size = 20,
    }: {
        xUserId?: string,
        xUserRole?: string,
        driverId?: string,
        page?: number,
        size?: number,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/billing/drivers/earnings/trips',
            headers: {
                'X-User-Id': xUserId,
                'X-User-Role': xUserRole,
            },
            query: {
                'driverId': driverId,
                'page': page,
                'size': size,
            },
        });
    }
    /**
     * Get driver earnings summary (today, this week, this month)
     * @returns any OK
     * @throws ApiError
     */
    public static summary({
        xUserId,
        xUserRole,
        driverId,
    }: {
        xUserId?: string,
        xUserRole?: string,
        driverId?: string,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/drivers/earnings/summary',
            headers: {
                'X-User-Id': xUserId,
                'X-User-Role': xUserRole,
            },
            query: {
                'driverId': driverId,
            },
        });
    }
    /**
     * Get driver earnings summary (today, this week, this month)
     * @returns any OK
     * @throws ApiError
     */
    public static summary1({
        xUserId,
        xUserRole,
        driverId,
    }: {
        xUserId?: string,
        xUserRole?: string,
        driverId?: string,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/billing/drivers/earnings/summary',
            headers: {
                'X-User-Id': xUserId,
                'X-User-Role': xUserRole,
            },
            query: {
                'driverId': driverId,
            },
        });
    }
}
