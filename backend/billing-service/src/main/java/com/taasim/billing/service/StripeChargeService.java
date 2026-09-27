package com.taasim.billing.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
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
     * Create + confirm a PaymentIntent in one call. Test mode uses {@code pm_card_visa}
     * which auto-confirms. In production the frontend supplies a real PaymentMethod.
     */
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

        PaymentIntent intent = PaymentIntent.create(params);

        if (!"succeeded".equals(intent.getStatus())) {
            throw new RuntimeException("PaymentIntent status: " + intent.getStatus() + " (id=" + intent.getId() + ")");
        }
        log.info("💳 Stripe charge OK: {} for trip {} (amount={} {})", intent.getId(), tripId, amountMinorUnits, currency);
        return intent.getId();
    }
}