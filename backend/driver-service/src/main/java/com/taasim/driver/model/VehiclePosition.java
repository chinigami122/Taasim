package com.taasim.driver.model;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

/**
 * Maps to the existing Cassandra table: taasim.vehicle_positions
 *
 * Primary Key: ((city, zone_id), event_time DESC)
 *   - Partition key: (city, zone_id) — all taxis in the same zone are co-located
 *   - Clustering key: event_time DESC — most recent position first
 */
@Table("vehicle_positions")
public class VehiclePosition {

    // ── Partition Key Part 1 ──
    @PrimaryKeyColumn(name = "city", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String city;

    // ── Partition Key Part 2 ──
    @PrimaryKeyColumn(name = "zone_id", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    private int zoneId;

    // ── Clustering Key ──
    @PrimaryKeyColumn(name = "event_time", ordinal = 2, type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.DESCENDING)
    private Instant eventTime;

    @Column("zone_name")
    private String zoneName;

    @Column("taxi_id")
    private String taxiId;

    @Column("lat")
    private double lat;

    @Column("lon")
    private double lon;

    @Column("speed")
    private double speed;

    @Column("status")
    private String status;

    // Default constructor
    public VehiclePosition() {}

    // Getters and Setters
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public int getZoneId() { return zoneId; }
    public void setZoneId(int zoneId) { this.zoneId = zoneId; }

    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public String getTaxiId() { return taxiId; }
    public void setTaxiId(String taxiId) { this.taxiId = taxiId; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}