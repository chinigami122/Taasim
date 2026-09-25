package com.taasim.matching.kafka;

import com.taasim.matching.service.MatchingEngine;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.mockito.Mockito.*;

class TripRequestConsumerTest {

    @Test
    void onTripRequest_withCoordinates_matchesAndPublishes() {
        MatchingEngine matchingEngine = mock(MatchingEngine.class);
        MatchEventProducer matchEventProducer = mock(MatchEventProducer.class);
        TripRequestConsumer consumer = new TripRequestConsumer(matchingEngine, matchEventProducer);

        String payload = """
            {"trip_id":"trip-123","origin_zone":5,"destination_zone":12,"origin_lat":33.5735,"origin_lon":-7.5895}
            """;

        when(matchingEngine.findNearestDriver(33.5735, -7.5895, 5)).thenReturn(Map.of(
                "driverId", "taxi_001",
                "distanceMeters", 1200.5,
                "etaSeconds", 300
        ));

        consumer.onTripRequest(payload);

        verify(matchingEngine).findNearestDriver(33.5735, -7.5895, 5);
        verify(matchEventProducer).send("trip-123", "taxi_001", 1200.5, 300, 5, 12);
    }

    @Test
    void onTripRequest_validPayloadAndMatchFound_publishesMatchEvent() {
        MatchingEngine matchingEngine = mock(MatchingEngine.class);
        MatchEventProducer matchEventProducer = mock(MatchEventProducer.class);
        TripRequestConsumer consumer = new TripRequestConsumer(matchingEngine, matchEventProducer);

        String payload = """
            {"trip_id":"trip-123","origin_zone":5,"destination_zone":12}
            """;

        when(matchingEngine.findNearestDriver(null, null, 5)).thenReturn(Map.of(
                "driverId", "taxi_001",
                "distanceMeters", 1200.5,
                "etaSeconds", 300
        ));

        consumer.onTripRequest(payload);

        verify(matchingEngine).findNearestDriver(null, null, 5);
        verify(matchEventProducer).send("trip-123", "taxi_001", 1200.5, 300, 5, 12);
    }

    @Test
    void onTripRequest_validPayloadButNoMatch_doesNotPublishEvent() {
        MatchingEngine matchingEngine = mock(MatchingEngine.class);
        MatchEventProducer matchEventProducer = mock(MatchEventProducer.class);
        TripRequestConsumer consumer = new TripRequestConsumer(matchingEngine, matchEventProducer);

        String payload = """
            {"trip_id":"trip-123","origin_zone":5,"destination_zone":12}
            """;

        when(matchingEngine.findNearestDriver(null, null, 5)).thenReturn(null);

        consumer.onTripRequest(payload);

        verify(matchingEngine).findNearestDriver(null, null, 5);
        verifyNoInteractions(matchEventProducer);
    }

    @Test
    void onTripRequest_malformedJson_doesNotThrowAndDoesNotInteract() {
        MatchingEngine matchingEngine = mock(MatchingEngine.class);
        MatchEventProducer matchEventProducer = mock(MatchEventProducer.class);
        TripRequestConsumer consumer = new TripRequestConsumer(matchingEngine, matchEventProducer);

        consumer.onTripRequest("not json at all");

        verifyNoInteractions(matchingEngine, matchEventProducer);
    }
}
