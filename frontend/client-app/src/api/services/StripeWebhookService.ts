/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class StripeWebhookService {
    /**
     * Handle Stripe Webhook events
     * Receives signed payment events from Stripe
     * @returns string OK
     * @throws ApiError
     */
    public static handleWebhook({
        requestBody,
        stripeSignature,
    }: {
        requestBody: string,
        stripeSignature?: string,
    }): CancelablePromise<string> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/billing/stripe/webhook',
            headers: {
                'Stripe-Signature': stripeSignature,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
