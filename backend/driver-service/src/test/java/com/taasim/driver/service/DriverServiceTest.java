package com.taasim.driver.service;

import com.taasim.driver.kafka.TripCompletedProducer;
import com.taasim.driver.kafka.TripStatusProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private TripStatusProducer tripStatusProducer;

    @Mock
    private TripCompletedProducer tripCompletedProducer;

    private DriverService driverService;

    @BeforeEach
    void setUp() {
        driverService = new DriverService(tripStatusProducer, tripCompletedProducer);
    }

    @Test
    void startRide_whenNoActiveTrip_returnsFalse() {
        boolean result = driverService.startRide("taxi_001");

        assertThat(result).isFalse();
        verifyNoInteractions(tripStatusProducer);
        verifyNoInteractions(tripCompletedProducer);
    }

    @Test
    void startRide_whenActiveTripExists_sendsInProgressAndReturnsTrue() {
        // Setup pending and accept to make it active
        driverService.assignTrip("taxi_001", "trip-100", 120);
        driverService.acceptTrip("taxi_001");

        boolean result = driverService.startRide("taxi_001");

        assertThat(result).isTrue();
        verify(tripStatusProducer).send("trip-100", "taxi_001", "IN_PROGRESS");
    }

    @Test
    void completeRide_whenNoActiveTrip_returnsFalse() {
        boolean result = driverService.completeRide("taxi_001");

        assertThat(result).isFalse();
        verifyNoInteractions(tripStatusProducer);
        verifyNoInteractions(tripCompletedProducer);
    }

    @Test
    void completeRide_whenActiveTripExists_sendsCompletedAndFiresTripCompletedEvent() {
        // Assign and accept
        driverService.assignTrip("taxi_001", "trip-100", 120);
        driverService.acceptTrip("taxi_001");
        assertThat(driverService.getActiveTrip("taxi_001")).isEqualTo("trip-100");
        assertThat(driverService.getDriverStatus("taxi_001")).isEqualTo("BUSY");

        boolean result = driverService.completeRide("taxi_001");

        assertThat(result).isTrue();
        assertThat(driverService.getActiveTrip("taxi_001")).isNull();
        assertThat(driverService.getDriverStatus("taxi_001")).isEqualTo("AVAILABLE");

        verify(tripStatusProducer).send("trip-100", "taxi_001", "COMPLETED");
        verify(tripCompletedProducer).send(eq("trip-100"), eq("taxi_001"), anyString(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void completeRide_withGpsMovement_calculatesAccumulatedDistance() {
        driverService.assignTrip("taxi_001", "trip-100", 120);
        driverService.acceptTrip("taxi_001");
        driverService.startRide("taxi_001");

        // First GPS ping at start
        driverService.recordGpsMovement("taxi_001", 33.5735, -7.5895);
        // Second GPS ping moved north
        driverService.recordGpsMovement("taxi_001", 33.5835, -7.5895);

        boolean result = driverService.completeRide("taxi_001");

        assertThat(result).isTrue();
        verify(tripCompletedProducer).send(
                eq("trip-100"),
                eq("taxi_001"),
                anyString(),
                doubleThat(d -> d > 1.0 && d < 1.3), // ~1.11 km
                anyDouble(),
                eq(1.0)
        );
    }

    @Test
    void rejectTrip_whenPending_sendsRejected() {
        driverService.assignTrip("taxi_001", "trip-200", 180);

        boolean result = driverService.rejectTrip("taxi_001");

        assertThat(result).isTrue();
        assertThat(driverService.getPendingTrip("taxi_001")).isNull();
        verify(tripStatusProducer).send("trip-200", "taxi_001", "REJECTED");
    }
}
