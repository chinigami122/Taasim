package com.taasim.billing.controller;

import com.taasim.billing.model.BillingRecord;
import com.taasim.billing.repository.BillingRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverEarningsControllerTest {

    @Mock
    private BillingRecordRepository repository;

    private DriverEarningsController controller;

    @BeforeEach
    void setUp() {
        controller = new DriverEarningsController(repository);
    }

    @Test
    void summary_returnsExpectedBucketsAndCurrency() {
        when(repository.sumPayoutSince(eq("driver-1"), any(Instant.class)))
                .thenReturn(new BigDecimal("120.50"));
        when(repository.countCompletedSince(eq("driver-1"), any(Instant.class)))
                .thenReturn(4L);

        ResponseEntity<?> response = controller.summary("driver-1", "DRIVER", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("currency")).isEqualTo("MAD");
        assertThat(body).containsKey("today");
        assertThat(body).containsKey("thisWeek");
        assertThat(body).containsKey("thisMonth");

        @SuppressWarnings("unchecked")
        Map<String, Object> today = (Map<String, Object>) body.get("today");
        assertThat(today.get("earnings")).isEqualTo(new BigDecimal("120.50"));
        assertThat(today.get("trips")).isEqualTo(4L);
    }

    @Test
    void summary_driverAccessingAnotherDriver_returns403() {
        ResponseEntity<?> response = controller.summary("driver-1", "DRIVER", "driver-2");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tripEarnings_returnsPaginatedRecords() {
        BillingRecord r = new BillingRecord();
        r.setTripId("trip-abc");
        r.setTotalFare(new BigDecimal("35.00"));
        r.setCommission(new BigDecimal("5.25"));
        r.setDriverPayout(new BigDecimal("29.75"));
        r.setDistanceKm(new BigDecimal("7.5"));
        r.setDurationMin(new BigDecimal("15.0"));
        r.setStatus("CHARGED");
        r.setChargedAt(Instant.now());

        when(repository.findByDriverId(eq("driver-1"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(r), PageRequest.of(0, 20), 1));

        ResponseEntity<?> response = controller.tripEarnings("driver-1", "DRIVER", null, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("totalPages")).isEqualTo(1);
        assertThat(body.get("totalElements")).isEqualTo(1L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trips = (List<Map<String, Object>>) body.get("trips");
        assertThat(trips).hasSize(1);
        assertThat(trips.get(0).get("tripId")).isEqualTo("trip-abc");
        assertThat(trips.get(0).get("payout")).isEqualTo(new BigDecimal("29.75"));
    }

    @Test
    void tripEarnings_driverAccessingAnotherDriver_returns403() {
        ResponseEntity<?> response = controller.tripEarnings("driver-1", "DRIVER", "driver-2", 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
