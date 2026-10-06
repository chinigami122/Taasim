package com.taasim.billing.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.taasim.billing.exception.StripeChargeFailedException;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StripeChargeService {

    private static final Logger log = LoggerFactory.getLogger(StripeChargeService.class);

    @Value("${stripe.secret-key:sk_test_placeholder_change_me}")
    private String secretKey;

    @Value("${stripe.currency:mad}")
    private String currency;

    @PostConstruct
    void init() {
        if (secretKey != null && !secretKey.isBlank() && !secretKey.contains("placeholder")) {
            Stripe.apiKey = secretKey;
            log.info("✅ Stripe initialized for billing-service (currency={})", currency);
        } else {
            log.warn("⚠️ STRIPE_SECRET_KEY not set or placeholder — Stripe charges will fail until configured");
        }
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank() && !secretKey.contains("placeholder");
    }

    /**
     * Create + confirm a PaymentIntent with a deterministic Idempotency-Key.
     * Protected by Resilience4j @Retry with exponential backoff on transient errors.
     */
    @Retry(name = "stripe-charge")
    public String chargeCustomer(String stripeCustomerId,
                                 String paymentMethodId,
                                 long amountMinorUnits,
                                 String description,
                                 String tripId) throws StripeException {

        if (Stripe.apiKey == null || Stripe.apiKey.contains("placeholder")) {
            Stripe.apiKey = secretKey;
        }

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountMinorUnits)
                .setCurrency(currency.toLowerCase())
                .setCustomer(stripeCustomerId)
                .setPaymentMethod(paymentMethodId)
                .setConfirm(true)
                .setOffSession(true)
                .setDescription(description)
                .putMetadata("trip_id", tripId)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                .build())
                .build();

        // Idempotency key: deterministic from trip_id → Stripe returns the same PaymentIntent
        // if the request is retried within 24 hours.
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("charge-trip-" + tripId)
                .build();

        PaymentIntent intent = PaymentIntent.create(params, options);

        if (!"succeeded".equals(intent.getStatus())) {
            throw new StripeChargeFailedException(
                    "PaymentIntent status: " + intent.getStatus() + " (id=" + intent.getId() + ")");
        }
        log.info("💳 Stripe charge OK: {} for trip {} (amount={} {})", intent.getId(), tripId, amountMinorUnits, currency);
        return intent.getId();
    }

    /**
     * Create an unconfirmed PaymentIntent returning client_secret for Stripe Elements on the frontend.
     */
    public java.util.Map<String, Object> createPaymentIntent(long amountMinorUnits,
                                                             String currency,
                                                             String tripId,
                                                             String description) throws StripeException {
        if (!isConfigured()) {
            log.info("🧪 Stripe not configured with real key — returning mock PaymentIntent client_secret for trip {}", tripId);
            return java.util.Map.of(
                    "clientSecret", "pi_mock_" + tripId + "_secret_mock",
                    "paymentIntentId", "pi_mock_" + tripId,
                    "amount", amountMinorUnits,
                    "status", "requires_payment_method"
            );
        }

        if (Stripe.apiKey == null || Stripe.apiKey.contains("placeholder")) {
            Stripe.apiKey = secretKey;
        }

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountMinorUnits)
                .setCurrency((currency != null ? currency : this.currency).toLowerCase())
                .setDescription(description)
                .putMetadata("trip_id", tripId)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build())
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("intent-trip-" + tripId)
                .build();

        PaymentIntent intent = PaymentIntent.create(params, options);
        log.info("💳 Stripe PaymentIntent created: {} (client_secret ready) for trip {}", intent.getId(), tripId);

        return java.util.Map.of(
                "clientSecret", intent.getClientSecret() != null ? intent.getClientSecret() : "",
                "paymentIntentId", intent.getId(),
                "amount", intent.getAmount(),
                "status", intent.getStatus()
        );
    }
}