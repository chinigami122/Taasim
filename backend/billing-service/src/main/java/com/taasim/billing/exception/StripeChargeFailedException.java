package com.taasim.billing.exception;

public class StripeChargeFailedException extends RuntimeException {
    public StripeChargeFailedException(String message) {
        super(message);
    }

    public StripeChargeFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
