package com.taasim.auth.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StripeCustomerService {

    private static final Logger log = LoggerFactory.getLogger(StripeCustomerService.class);

    @Value("${stripe.secret-key:sk_test_placeholder_change_me}")
    private String secretKey;

    @PostConstruct
    void init() {
        if (secretKey != null && !secretKey.isBlank() && !secretKey.contains("placeholder")) {
            Stripe.apiKey = secretKey;
            log.info("✅ Stripe initialized for auth-service");
        } else {
            log.warn("⚠️ STRIPE_SECRET_KEY not set or placeholder — Stripe customer creation will be skipped until configured");
        }
    }

    public String createCustomer(String email, String fullName) throws StripeException {
        // Ensure apiKey is set (covers case where @Value injected after @PostConstruct in tests)
        if (Stripe.apiKey == null || Stripe.apiKey.contains("placeholder")) {
            Stripe.apiKey = secretKey;
        }
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .setName(fullName)
                .setDescription("TaaSim client")
                .build();
        Customer customer = Customer.create(params);
        log.info("💳 Created Stripe customer {} for {}", customer.getId(), email);
        return customer.getId();
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank() && !secretKey.contains("placeholder");
    }
}