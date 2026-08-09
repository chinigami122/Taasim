package com.taasim.trip.service;

import com.taasim.trip.dto.TripRequestDto;
import com.taasim.trip.kafka.TripEventProducer;
import com.taasim.trip.model.Trip;
import com.taasim.trip.repository.TripRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final TripEventProducer tripEventProducer;

    public TripService(TripRepository tripRepository, TripEventProducer tripEventProducer) {
        this.tripRepository = tripRepository;
        this.tripEventProducer = tripEventProducer;
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

        // Publish to Kafka
        tripEventProducer.send(tripId, request.getRiderId(),
                request.getOriginZone(), request.getDestinationZone(),
                now.toEpochMilli());

        System.out.println("🚕 Trip created: " + tripId + " | "
                + request.getOriginZone() + " → " + request.getDestinationZone());

        return trip;
    }
}