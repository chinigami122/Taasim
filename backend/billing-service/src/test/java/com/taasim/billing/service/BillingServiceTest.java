package com.taasim.billing.service;

import com.taasim.billing.model.BillingRecord;
import com.taasim.billing.repository.BillingRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private BillingRecordRepository repository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private StripeChargeService stripeChargeService;

    @Mock
    private ClientLookupService clientLookupService;

    private FareCalculator calculator;
    private BillingService billingService;

    @BeforeEach
    void setUp() throws Exception {
        calculator = new FareCalculator();
        var f1 = FareCalculator.class.getDeclaredField("baseFare");
        f1.setAccessible(true);
        f1.set(calculator, new BigDecimal("5.00"));

        var f2 = FareCalculator.class.getDeclaredField("perKm");
        f2.setAccessible(true);
        f2.set(calculator, new BigDecimal("3.50"));

        var f3 = FareCalculator.class.getDeclaredField("perMinute");
        f3.setAccessible(true);
        f3.set(calculator, new BigDecimal("0.50"));

        var f4 = FareCalculator.class.getDeclaredField("commissionRate");
        f4.setAccessible(true);
        f4.set(calculator, new BigDecimal("0.15"));

        billingService = new BillingService(repository, calculator, kafkaTemplate, stripeChargeService,
                clientLookupService);
    }

    @Test
    void createBillingFor_newTrip_calculatesSavesAndPublishesEvent() {
        when(repository.existsByTripId("trip-123")).thenReturn(false);
        when(repository.save(any(BillingRecord.class))).thenAnswer(invocation -> {
            BillingRecord r = invocation.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
        when(stripeChargeService.isConfigured()).thenReturn(false);

        BillingRecord result = billingService.createBillingFor(
                "trip-123", "driver-1", "rider-1", 5.0, 10.0, 1.0);

        assertThat(result).isNotNull();
        assertThat(result.getTripId()).isEqualTo("trip-123");
        assertThat(result.getTotalFare()).isEqualByComparingTo("27.50");
        assertThat(result.getDriverPayout()).isEqualByComparingTo("23.37");
        assertThat(result.getStatus()).isEqualTo("CALCULATED");

        verify(repository, atLeastOnce()).save(any(BillingRecord.class));
        verify(kafkaTemplate, times(1)).send(eq("billing.completed"), eq("trip-123"), any());
    }

    @Test
    void createBillingFor_withStripe_chargesSuccessfully() throws Exception {
        when(repository.existsByTripId("trip-123")).thenReturn(false);
        when(repository.save(any(BillingRecord.class))).thenAnswer(invocation -> {
            BillingRecord r = invocation.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
        when(stripeChargeService.isConfigured()).thenReturn(true);
        when(clientLookupService.getStripeCustomerId("rider-1")).thenReturn("cus_test123");
        when(stripeChargeService.chargeCustomer(eq("cus_test123"), anyString(), eq(2750L), anyString(), eq("trip-123")))
                .thenReturn("pi_test_abc123");

        BillingRecord result = billingService.createBillingFor(
                "trip-123", "driver-1", "rider-1", 5.0, 10.0, 1.0);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("CHARGED");
        assertThat(result.getStripePaymentId()).isEqualTo("pi_test_abc123");
        assertThat(result.getChargedAt()).isNotNull();

        verify(stripeChargeService, times(1)).chargeCustomer(eq("cus_test123"), anyString(), eq(2750L), anyString(),
                eq("trip-123"));
    }

    @Test
    void createBillingFor_existingTrip_isIdempotentAndDoesNotDuplicate() {
        BillingRecord existing = new BillingRecord();
        existing.setId(UUID.randomUUID());
        existing.setTripId("trip-123");
        existing.setTotalFare(new BigDecimal("27.50"));

        when(repository.existsByTripId("trip-123")).thenReturn(true);
        when(repository.findByTripId("trip-123")).thenReturn(Optional.of(existing));

        BillingRecord result = billingService.createBillingFor(
                "trip-123", "driver-1", "rider-1", 5.0, 10.0, 1.0);

        assertThat(result).isSameAs(existing);
        verify(repository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void createBillingFor_stripeFails_incrementsAttemptsAndSetsError() throws Exception {
        when(repository.existsByTripId("trip-123")).thenReturn(false);
        when(repository.save(any(BillingRecord.class))).thenAnswer(invocation -> {
            BillingRecord r = invocation.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
        when(stripeChargeService.isConfigured()).thenReturn(true);
        when(clientLookupService.getStripeCustomerId("rider-1")).thenReturn("cus_test123");
        when(stripeChargeService.chargeCustomer(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Card declined"));

        BillingRecord result = billingService.createBillingFor(
                "trip-123", "driver-1", "rider-1", 5.0, 10.0, 1.0);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("CALCULATED");
        assertThat(result.getChargeAttempts()).isEqualTo(1);
        assertThat(result.getLastChargeError()).isEqualTo("Card declined");
        assertThat(result.getLastAttemptedAt()).isNotNull();
    }

    @Test
    void retryCharge_stripeFails5Times_setsChargeFailed() throws Exception {
        BillingRecord record = new BillingRecord();
        record.setId(UUID.randomUUID());
        record.setTripId("trip-123");
        record.setClientId("rider-1");
        record.setStatus("CALCULATED");
        record.setTotalFare(new BigDecimal("27.50"));
        record.setChargeAttempts(4);

        when(stripeChargeService.isConfigured()).thenReturn(true);
        when(clientLookupService.getStripeCustomerId("rider-1")).thenReturn("cus_test123");
        when(stripeChargeService.chargeCustomer(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Connection timeout"));
        when(repository.save(any(BillingRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        billingService.retryCharge(record);

        assertThat(record.getStatus()).isEqualTo("CHARGE_FAILED");
        assertThat(record.getChargeAttempts()).isEqualTo(5);
        assertThat(record.getLastChargeError()).isEqualTo("Connection timeout");
    }
}
