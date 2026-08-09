package com.taasim.trip.model;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

/**
 * Maps to existing Cassandra table: taasim.trips
 *
 * Primary Key: ((city, date_bucket), created_at DESC)
 *
 * From cassandra_schema.cql:
 *   city, date_bucket, created_at, trip_id, rider_id, taxi_id,
 *   origin_zone, dest_zone, status, pickup_time, dropoff_time,
 *   fare, eta_seconds
 */
@Table("trips")
public class Trip {

    @PrimaryKeyColumn(name = "city", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String city;

    @PrimaryKeyColumn(name = "date_bucket", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    private String dateBucket;

    @PrimaryKeyColumn(name = "created_at", ordinal = 2, type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.DESCENDING)
    private Instant createdAt;

    @Column("trip_id")
    private String tripId;

    @Column("rider_id")
    private String riderId;

    @Column("taxi_id")
    private String taxiId;

    @Column("origin_zone")
    private int originZone;

    @Column("dest_zone")
    private int destZone;

    @Column("status")
    private String status;

    @Column("pickup_time")
    private Instant pickupTime;

    @Column("dropoff_time")
    private Instant dropoffTime;

    @Column("fare")
    private Double fare;

    @Column("eta_seconds")
    private Integer etaSeconds;

    public Trip() {}

    // Getters and setters
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDateBucket() { return dateBucket; }
    public void setDateBucket(String dateBucket) { this.dateBucket = dateBucket; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }

    public String getRiderId() { return riderId; }
    public void setRiderId(String riderId) { this.riderId = riderId; }

    public String getTaxiId() { return taxiId; }
    public void setTaxiId(String taxiId) { this.taxiId = taxiId; }

    public int getOriginZone() { return originZone; }
    public void setOriginZone(int originZone) { this.originZone = originZone; }

    public int getDestZone() { return destZone; }
    public void setDestZone(int destZone) { this.destZone = destZone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getPickupTime() { return pickupTime; }
    public void setPickupTime(Instant pickupTime) { this.pickupTime = pickupTime; }

    public Instant getDropoffTime() { return dropoffTime; }
    public void setDropoffTime(Instant dropoffTime) { this.dropoffTime = dropoffTime; }

    public Double getFare() { return fare; }
    public void setFare(Double fare) { this.fare = fare; }

    public Integer getEtaSeconds() { return etaSeconds; }
    public void setEtaSeconds(Integer etaSeconds) { this.etaSeconds = etaSeconds; }
}