package com.taasim.driver.kafka;

import com.taasim.driver.service.DriverService;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class MatchEventConsumerTest {

    @Test
    void onMatch_validPayload_callsAssignTrip() {
        DriverService driverService = mock(DriverService.class);
        MatchEventConsumer consumer = new MatchEventConsumer(driverService);

        String payload = """
            {"trip_id":"abc-123","driver_id":"taxi_001","eta_seconds":180}
            """;

        consumer.onMatch(payload);

        verify(driverService).assignTrip("taxi_001", "abc-123", 180);
    }

    @Test
    void onMatch_malformedJson_doesNotThrow() {
        DriverService driverService = mock(DriverService.class);
        MatchEventConsumer consumer = new MatchEventConsumer(driverService);

        consumer.onMatch("not json at all");

        verifyNoInteractions(driverService);
    }

    @Test
    void onMatch_missingField_doesNotThrow() {
        DriverService driverService = mock(DriverService.class);
        MatchEventConsumer consumer = new MatchEventConsumer(driverService);

        consumer.onMatch("{\"trip_id\":\"abc\"}");

        verifyNoInteractions(driverService);
    }
}
