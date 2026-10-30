package com.taasim.billing.controller;

import com.taasim.billing.config.SecurityConfig;
import com.taasim.billing.model.BillingRecord;
import com.taasim.billing.repository.BillingRecordRepository;
import com.taasim.billing.service.StripeChargeService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BillingController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=dev-only-secret-please-change-me-it-must-be-256-bits-long-abcd1234")
class BillingSecurityTest {
    private static final String SECRET = "dev-only-secret-please-change-me-it-must-be-256-bits-long-abcd1234";
    @Autowired MockMvc mvc;
    @MockitoBean BillingRecordRepository repository;
    @MockitoBean StripeChargeService stripe;

    private String token(String id, String role) {
        return Jwts.builder().subject(id).claim("role", role).issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60000))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
    @Test void clientCannotListAnotherClientsBilling() throws Exception {
        mvc.perform(get("/api/billing/clients/client-a").header("Authorization", "Bearer " + token("client-b", "CLIENT")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void driverCannotListAnotherDriversBilling() throws Exception {
        mvc.perform(get("/api/billing/drivers/driver-a").header("Authorization", "Bearer " + token("driver-b", "DRIVER")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void forgedHeadersDoNotGrantFareAccessOnDirectServicePort() throws Exception {
        BillingRecord bill = new BillingRecord(); bill.setTripId("trip-a"); bill.setClientId("client-a"); bill.setDriverId("driver-a");
        when(repository.findByTripId("trip-a")).thenReturn(Optional.of(bill));
        mvc.perform(get("/api/billing/trips/trip-a").header("Authorization", "Bearer " + token("client-b", "CLIENT"))
            .header("X-User-Role", "ADMIN").header("X-User-Id", "client-a"))
            .andExpect(status().isForbidden());
    }
    @Test void ownerCanReadFareWithoutGatewayHeaders() throws Exception {
        BillingRecord bill = new BillingRecord(); bill.setTripId("trip-a"); bill.setClientId("client-a"); bill.setDriverId("driver-a");
        when(repository.findByTripId("trip-a")).thenReturn(Optional.of(bill));
        mvc.perform(get("/api/billing/trips/trip-a").header("Authorization", "Bearer " + token("client-a", "CLIENT")))
            .andExpect(status().isOk());
    }
    @Test void adminCanReadAnyFare() throws Exception {
        BillingRecord bill = new BillingRecord(); bill.setTripId("trip-a"); bill.setClientId("client-a"); bill.setDriverId("driver-a");
        when(repository.findByTripId("trip-a")).thenReturn(Optional.of(bill));
        mvc.perform(get("/api/billing/trips/trip-a").header("Authorization", "Bearer " + token("admin", "ADMIN")))
            .andExpect(status().isOk());
    }
}
