package com.taasim.trip.service;

import com.taasim.trip.dto.TripRequestDto;
import com.taasim.trip.kafka.TripEventProducer;
import com.taasim.trip.model.Trip;
import com.taasim.trip.repository.TripRepository;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final TripEventProducer tripEventProducer;
    private final CassandraTemplate cassandraTemplate;
    private final Map<String, Trip> tripCache = new ConcurrentHashMap<>();

    public TripService(TripRepository tripRepository, TripEventProducer tripEventProducer,
                       CassandraTemplate cassandraTemplate) {
        this.tripRepository = tripRepository;
        this.tripEventProducer = tripEventProducer;
        this.cassandraTemplate = cassandraTemplate;
    }

    /**
     * Create a new trip request.
     * 1. Generate a unique trip ID
     * 2. Save to Cassandra with status REQUESTED
     * 3. Publish to Kafka raw.trips
     */
    public Trip createTrip(TripRequestDto request) {
        String tripId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String dateBucket = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneOffset.UTC)
                .format(now);

        Trip trip = new Trip();
        trip.setCity("casablanca");
        trip.setDateBucket(dateBucket);
        trip.setCreatedAt(now);
        trip.setTripId(tripId);
        trip.setRiderId(request.getRiderId());
        trip.setOriginZone(request.getOriginZone());
        trip.setDestZone(request.getDestinationZone());
        trip.setStatus("REQUESTED");

        // Save to Cassandra
        tripRepository.save(trip);

        tripCache.put(tripId, trip);

        // Publish to Kafka
        tripEventProducer.send(tripId, request.getRiderId(),
                request.getOriginZone(), request.getDestinationZone(),
                now.toEpochMilli());

        System.out.println("🚕 Trip created: " + tripId + " | "
                + request.getOriginZone() + " → " + request.getDestinationZone());

        return trip;
    }
    /**
     * Update trip to MATCHED status with the assigned driver.
     * Uses CQL directly because the primary key is composite.
     */
    public void updateTripToMatched(String tripId, String driverId, int etaSeconds) {
        Trip trip = tripCache.get(tripId);
        if (trip == null) {
            System.err.println("⚠️ Trip not found in cache: " + tripId);
            return;
        }

        cassandraTemplate.getCqlOperations().execute(
                "UPDATE taasim.trips SET status = 'MATCHED', taxi_id = ?, eta_seconds = ? " +
                        "WHERE city = ? AND date_bucket = ? AND created_at = ?",
                driverId, etaSeconds, trip.getCity(), trip.getDateBucket(), trip.getCreatedAt()
        );

        trip.setStatus("MATCHED");
        trip.setTaxiId(driverId);
        trip.setEtaSeconds(etaSeconds);

        System.out.println("✅ Trip " + tripId + " → MATCHED");
    }

    public Trip getTripById(String tripId) {
        return tripCache.get(tripId);
    }
}