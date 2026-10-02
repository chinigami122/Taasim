package com.taasim.trip.service;

import com.taasim.common.util.GeoUtils;
import com.taasim.trip.dto.TripRequestDto;
import com.taasim.trip.kafka.TripEventProducer;
import com.taasim.trip.model.Trip;
import com.taasim.trip.repository.TripRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final Counter tripsCreated;

    @Autowired
    public TripService(TripRepository tripRepository, TripEventProducer tripEventProducer,
                       CassandraTemplate cassandraTemplate,
                       @Autowired(required = false) MeterRegistry meterRegistry) {
        this.tripRepository = tripRepository;
        this.tripEventProducer = tripEventProducer;
        this.cassandraTemplate = cassandraTemplate;
        MeterRegistry registry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.tripsCreated = registry.counter("taasim.trips.created");
    }

    public TripService(TripRepository tripRepository, TripEventProducer tripEventProducer,
                       CassandraTemplate cassandraTemplate) {
        this(tripRepository, tripEventProducer, cassandraTemplate, new SimpleMeterRegistry());
    }

    /**
     * Create a new trip request.
     * 1. Determine origin & destination zones (from explicit zone or derived from GPS)
     * 2. Save to Cassandra with status REQUESTED
     * 3. Publish to Kafka raw.trips
     */
    public Trip createTrip(TripRequestDto request) {
        return createTrip(request, null);
    }

    public Trip createTrip(TripRequestDto request, String authenticatedUserId) {
        String tripId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String dateBucket = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneOffset.UTC)
                .format(now);

        int originZone = resolveOriginZone(request);
        int destZone = resolveDestinationZone(request);

        String riderId = request.getRiderId();
        if ((riderId == null || riderId.isBlank()) && authenticatedUserId != null && !authenticatedUserId.isBlank()) {
            riderId = authenticatedUserId;
        }
        if (riderId == null || riderId.isBlank()) {
            riderId = "unknown";
        }

        Trip trip = new Trip();
        trip.setCity("casablanca");
        trip.setDateBucket(dateBucket);
        trip.setCreatedAt(now);
        trip.setTripId(tripId);
        trip.setRiderId(riderId);
        trip.setOriginZone(originZone);
        trip.setDestZone(destZone);
        trip.setStatus("REQUESTED");

        // Save to Cassandra
        tripRepository.save(trip);

        tripCache.put(tripId, trip);

        // Publish to Kafka with both coordinates and zones
        tripEventProducer.send(
                tripId,
                riderId,
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

        tripsCreated.increment();
        return trip;
    }

    /**
     * List recent trips for an authenticated client.
     */
    public java.util.List<Map<String, Object>> findTripsForClient(String clientId, int limit, String fromDate) {
        java.time.LocalDate today = java.time.LocalDate.now(ZoneOffset.UTC);
        java.util.List<String> buckets = java.util.List.of(
                today.toString(),
                today.minusDays(1).toString(),
                today.minusDays(2).toString()
        );

        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();

        for (String bucket : buckets) {
            var rows = cassandraTemplate.getCqlOperations().queryForList(
                    "SELECT trip_id, status, taxi_id, eta_seconds, origin_zone, dest_zone, created_at, rider_id " +
                            "FROM taasim.trips WHERE city = 'casablanca' AND date_bucket = ?",
                    bucket
            );

            for (Map<String, Object> r : rows) {
                String rider = (String) r.get("rider_id");
                if (clientId.equals(rider)) {
                    Object createdAtObj = r.get("created_at");
                    String createdAtStr = createdAtObj != null ? createdAtObj.toString() : "";
                    results.add(Map.of(
                            "tripId",     r.get("trip_id") != null ? r.get("trip_id") : "",
                            "status",     r.get("status") != null ? r.get("status") : "",
                            "driverId",   r.get("taxi_id") != null ? r.get("taxi_id") : "",
                            "etaSeconds", r.get("eta_seconds") != null ? r.get("eta_seconds") : 0,
                            "originZone", r.get("origin_zone") != null ? r.get("origin_zone") : 1,
                            "destZone",   r.get("dest_zone") != null ? r.get("dest_zone") : 1,
                            "createdAt",  createdAtStr
                    ));
                }
            }
            if (results.size() >= limit) {
                break;
            }
        }

        // Also check in-memory cache for recent trips in case Cassandra propagation is pending
        for (Trip cached : tripCache.values()) {
            if (clientId.equals(cached.getRiderId())) {
                boolean alreadyInResults = results.stream().anyMatch(m -> cached.getTripId().equals(m.get("tripId")));
                if (!alreadyInResults) {
                    results.add(0, Map.of(
                            "tripId",     cached.getTripId(),
                            "status",     cached.getStatus() != null ? cached.getStatus() : "",
                            "driverId",   cached.getTaxiId() != null ? cached.getTaxiId() : "",
                            "etaSeconds", cached.getEtaSeconds() != null ? cached.getEtaSeconds() : 0,
                            "originZone", cached.getOriginZone(),
                            "destZone",   cached.getDestZone(),
                            "createdAt",  cached.getCreatedAt() != null ? cached.getCreatedAt().toString() : ""
                    ));
                }
            }
        }

        return results.stream().limit(limit).toList();
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

    /**
     * Admin query to list trips with optional status and date filters.
     */
    public java.util.List<Map<String, Object>> adminListTrips(String status, String date, int limit) {
        String bucket = (date != null && !date.isBlank())
                ? date.trim()
                : DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(Instant.now());

        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
        if (cassandraTemplate != null) {
            try {
                var rows = cassandraTemplate.getCqlOperations().queryForList(
                        "SELECT trip_id, status, taxi_id, rider_id, eta_seconds, origin_zone, dest_zone, created_at, fare " +
                                "FROM taasim.trips WHERE city = 'casablanca' AND date_bucket = ?",
                        bucket
                );
                for (Map<String, Object> r : rows) {
                    String tripStatus = (String) r.get("status");
                    if (status != null && !status.isBlank() && !status.equalsIgnoreCase(tripStatus)) {
                        continue;
                    }
                    Object createdAtObj = r.get("created_at");
                    results.add(Map.of(
                            "tripId",     r.get("trip_id") != null ? r.get("trip_id") : "",
                            "status",     tripStatus != null ? tripStatus : "",
                            "driverId",   r.get("taxi_id") != null ? r.get("taxi_id") : "",
                            "riderId",    r.get("rider_id") != null ? r.get("rider_id") : "",
                            "etaSeconds", r.get("eta_seconds") != null ? r.get("eta_seconds") : 0,
                            "originZone", r.get("origin_zone") != null ? r.get("origin_zone") : 1,
                            "destZone",   r.get("dest_zone") != null ? r.get("dest_zone") : 1,
                            "createdAt",  createdAtObj != null ? createdAtObj.toString() : "",
                            "fare",       r.get("fare") != null ? r.get("fare") : 0.0
                    ));
                }
            } catch (Exception e) {
                System.err.println("⚠️ Error querying trips from Cassandra: " + e.getMessage());
            }
        }

        // Overlay with in-memory tripCache for the target date
        for (Trip cached : tripCache.values()) {
            if (bucket.equals(cached.getDateBucket())) {
                String tripStatus = cached.getStatus();
                if (status != null && !status.isBlank() && !status.equalsIgnoreCase(tripStatus)) {
                    continue;
                }
                boolean exists = results.stream().anyMatch(m -> cached.getTripId().equals(m.get("tripId")));
                if (!exists) {
                    results.add(0, Map.of(
                            "tripId",     cached.getTripId(),
                            "status",     tripStatus != null ? tripStatus : "",
                            "driverId",   cached.getTaxiId() != null ? cached.getTaxiId() : "",
                            "riderId",    cached.getRiderId() != null ? cached.getRiderId() : "",
                            "etaSeconds", cached.getEtaSeconds() != null ? cached.getEtaSeconds() : 0,
                            "originZone", cached.getOriginZone(),
                            "destZone",   cached.getDestZone(),
                            "createdAt",  cached.getCreatedAt() != null ? cached.getCreatedAt().toString() : "",
                            "fare",       cached.getFare() != null ? cached.getFare() : 0.0
                    ));
                }
            }
        }

        return results.stream().limit(limit).toList();
    }

    /**
     * Compute trip statistics for a given date.
     */
    public Map<String, Object> tripStatsForDate(String date) {
        String bucket = (date != null && !date.isBlank())
                ? date.trim()
                : DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(Instant.now());

        java.util.List<Map<String, Object>> trips = adminListTrips(null, bucket, 10000);

        long requested = 0;
        long matched = 0;
        long inProgress = 0;
        long completed = 0;
        long cancelled = 0;
        long totalWaitSeconds = 0;
        long waitCount = 0;

        for (Map<String, Object> t : trips) {
            String s = ((String) t.getOrDefault("status", "")).toUpperCase();
            switch (s) {
                case "REQUESTED" -> requested++;
                case "MATCHED", "ACCEPTED" -> {
                    matched++;
                    int eta = (Integer) t.getOrDefault("etaSeconds", 0);
                    if (eta > 0) {
                        totalWaitSeconds += eta;
                        waitCount++;
                    }
                }
                case "IN_PROGRESS" -> inProgress++;
                case "COMPLETED" -> {
                    completed++;
                    int eta = (Integer) t.getOrDefault("etaSeconds", 0);
                    if (eta > 0) {
                        totalWaitSeconds += eta;
                        waitCount++;
                    }
                }
                case "CANCELLED", "REJECTED" -> cancelled++;
                default -> requested++;
            }
        }

        long avgWaitSeconds = waitCount > 0 ? (totalWaitSeconds / waitCount) : 0;

        return Map.of(
                "date", bucket,
                "totalTrips", trips.size(),
                "requested", requested,
                "matched", matched,
                "inProgress", inProgress,
                "completed", completed,
                "cancelled", cancelled,
                "avg_wait_seconds", avgWaitSeconds
        );
    }
}