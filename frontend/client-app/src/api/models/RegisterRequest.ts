/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
/**
 * User registration payload
 */
export type RegisterRequest = {
    /**
     * User email address
     */
    email: string;
    /**
     * Account password (plain text, will be hashed with BCrypt)
     */
    password: string;
    /**
     * Full name of the user
     */
    fullName?: string;
    /**
     * Contact phone number
     */
    phone?: string;
    /**
     * User role in the platform
     */
    role?: RegisterRequest.role;
};
export namespace RegisterRequest {
    /**
     * User role in the platform
     */
    export enum role {
        CLIENT = 'CLIENT',
        DRIVER = 'DRIVER',
        ADMIN = 'ADMIN',
    }
}

