package com.taasim.trip.service;

import com.taasim.common.util.GeoUtils;
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
     * 1. Determine origin & destination zones (from explicit zone or derived from GPS)
     * 2. Save to Cassandra with status REQUESTED
     * 3. Publish to Kafka raw.trips
     */
    public Trip createTrip(TripRequestDto request) {
        String tripId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String dateBucket = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneOffset.UTC)
                .format(now);

        int originZone = resolveOriginZone(request);
        int destZone = resolveDestinationZone(request);

        Trip trip = new Trip();
        trip.setCity("casablanca");
        trip.setDateBucket(dateBucket);
        trip.setCreatedAt(now);
        trip.setTripId(tripId);
        trip.setRiderId(request.getRiderId());
        trip.setOriginZone(originZone);
        trip.setDestZone(destZone);
        trip.setStatus("REQUESTED");

        // Save to Cassandra
        tripRepository.save(trip);

        tripCache.put(tripId, trip);

        // Publish to Kafka with both coordinates and zones
        tripEventProducer.send(
                tripId,
                request.getRiderId(),
                originZone,
                destZone,
                request.getOriginLat(),
                request.getOriginLon(),
                request.getDestinationLat(),
                request.getDestinationLon(),
                now.toEpochMilli()
        );

        System.out.println("🚕 Trip created: " + tripId + " | Zone "
                + originZone + " → Zone " + destZone
                + (request.getOriginLat() != null ? " (GPS: " + request.getOriginLat() + ", " + request.getOriginLon() + ")" : ""));

        return trip;
    }

    private int resolveOriginZone(TripRequestDto request) {
        if (request.getOriginZone() != null && request.getOriginZone() >= 1 && request.getOriginZone() <= 16) {
            return request.getOriginZone();
        }
        if (request.getOriginLat() != null && request.getOriginLon() != null) {
            return GeoUtils.calculateZoneId(request.getOriginLat(), request.getOriginLon());
        }
        return 1;
    }

    private int resolveDestinationZone(TripRequestDto request) {
        if (request.getDestinationZone() != null && request.getDestinationZone() >= 1 && request.getDestinationZone() <= 16) {
            return request.getDestinationZone();
        }
        if (request.getDestinationLat() != null && request.getDestinationLon() != null) {
            return GeoUtils.calculateZoneId(request.getDestinationLat(), request.getDestinationLon());
        }
        return 1;
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

    public void updateTripStatus(String tripId, String newStatus) {
        Trip trip = tripCache.get(tripId);
        if (trip == null) {
            System.err.println("⚠️ Trip not found: " + tripId);
            return;
        }
        trip.setStatus(newStatus);

        cassandraTemplate.getCqlOperations().execute(
                "UPDATE taasim.trips SET status = ? " +
                        "WHERE city = ? AND date_bucket = ? AND created_at = ?",
                newStatus, trip.getCity(), trip.getDateBucket(), trip.getCreatedAt()
        );
    }

    public Trip getTripById(String tripId) {
        return tripCache.get(tripId);
    }
}