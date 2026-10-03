/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class InternalUserService {
    /**
     * Get Stripe customer ID for a user
     * Used by billing-service to charge clients
     * @returns any OK
     * @throws ApiError
     */
    public static getStripeCustomerId({
        id,
    }: {
        id: string,
    }): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/internal/users/{id}/stripe-customer',
            path: {
                'id': id,
            },
        });
    }
}
