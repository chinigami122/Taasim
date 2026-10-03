/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class AdminDriversService {
    /**
     * List all drivers with status filtering and pagination
     * @returns any OK
     * @throws ApiError
     */
    public static listAll({
        status,
        page,
        size = 50,
    }: {
        status?: string,
        page?: number,
        size?: number,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/drivers',
            query: {
                'status': status,
                'page': page,
                'size': size,
            },
        });
    }
    /**
     * Get detailed driver profile with recent trips
     * @returns any OK
     * @throws ApiError
     */
    public static getDriverDetail({
        driverId,
    }: {
        driverId: string,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/drivers/{driverId}',
            path: {
                'driverId': driverId,
            },
        });
    }
    /**
     * Get live GPS positions of all active drivers
     * @returns any OK
     * @throws ApiError
     */
    public static livePositions(): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/drivers/live-positions',
        });
    }
}
