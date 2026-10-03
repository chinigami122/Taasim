/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type BillingRecord = {
    id?: string;
    tripId?: string;
    driverId?: string;
    clientId?: string;
    distanceKm?: number;
    durationMin?: number;
    surgeMultiplier?: number;
    baseFare?: number;
    distanceFare?: number;
    timeFare?: number;
    totalFare?: number;
    commission?: number;
    driverPayout?: number;
    currency?: string;
    status?: string;
    stripePaymentId?: string;
    createdAt?: string;
    chargedAt?: string;
    chargeAttempts?: number;
    lastChargeError?: string;
    lastAttemptedAt?: string;
};

