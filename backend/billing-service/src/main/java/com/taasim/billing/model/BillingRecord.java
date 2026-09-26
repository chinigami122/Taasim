package com.taasim.billing.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_records")
public class BillingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trip_id", nullable = false, unique = true)
    private String tripId;

    @Column(name = "driver_id", nullable = false)
    private String driverId;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "distance_km", precision = 8, scale = 3)
    private BigDecimal distanceKm;

    @Column(name = "duration_min", precision = 6, scale = 2)
    private BigDecimal durationMin;

    @Column(name = "surge_multiplier", precision = 3, scale = 2)
    private BigDecimal surgeMultiplier;

    @Column(name = "base_fare", precision = 8, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "distance_fare", precision = 8, scale = 2)
    private BigDecimal distanceFare;

    @Column(name = "time_fare", precision = 8, scale = 2)
    private BigDecimal timeFare;

    @Column(name = "total_fare", precision = 8, scale = 2)
    private BigDecimal totalFare;

    @Column(precision = 8, scale = 2)
    private BigDecimal commission;

    @Column(name = "driver_payout", precision = 8, scale = 2)
    private BigDecimal driverPayout;

    @Column(nullable = false)
    private String currency = "MAD";

    @Column(nullable = false)
    private String status = "CALCULATED";

    @Column(name = "stripe_payment_id")
    private String stripePaymentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "charged_at")
    private Instant chargedAt;

    public BillingRecord() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }

    public BigDecimal getDurationMin() { return durationMin; }
    public void setDurationMin(BigDecimal durationMin) { this.durationMin = durationMin; }

    public BigDecimal getSurgeMultiplier() { return surgeMultiplier; }
    public void setSurgeMultiplier(BigDecimal surgeMultiplier) { this.surgeMultiplier = surgeMultiplier; }

    public BigDecimal getBaseFare() { return baseFare; }
    public void setBaseFare(BigDecimal baseFare) { this.baseFare = baseFare; }

    public BigDecimal getDistanceFare() { return distanceFare; }
    public void setDistanceFare(BigDecimal distanceFare) { this.distanceFare = distanceFare; }

    public BigDecimal getTimeFare() { return timeFare; }
    public void setTimeFare(BigDecimal timeFare) { this.timeFare = timeFare; }

    public BigDecimal getTotalFare() { return totalFare; }
    public void setTotalFare(BigDecimal totalFare) { this.totalFare = totalFare; }

    public BigDecimal getCommission() { return commission; }
    public void setCommission(BigDecimal commission) { this.commission = commission; }

    public BigDecimal getDriverPayout() { return driverPayout; }
    public void setDriverPayout(BigDecimal driverPayout) { this.driverPayout = driverPayout; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStripePaymentId() { return stripePaymentId; }
    public void setStripePaymentId(String stripePaymentId) { this.stripePaymentId = stripePaymentId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getChargedAt() { return chargedAt; }
    public void setChargedAt(Instant chargedAt) { this.chargedAt = chargedAt; }
}
