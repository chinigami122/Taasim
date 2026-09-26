package com.taasim.billing.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FareCalculator {

    @Value("${billing.fare.base:5.00}")
    private BigDecimal baseFare;

    @Value("${billing.fare.per-km:3.50}")
    private BigDecimal perKm;

    @Value("${billing.fare.per-minute:0.50}")
    private BigDecimal perMinute;

    @Value("${billing.fare.commission-rate:0.15}")
    private BigDecimal commissionRate;

    public FareBreakdown calculate(double distanceKm, double durationMin, double surge) {
        BigDecimal dKm = BigDecimal.valueOf(distanceKm);
        BigDecimal dMin = BigDecimal.valueOf(durationMin);
        BigDecimal sMul = BigDecimal.valueOf(surge);

        BigDecimal distFare = perKm.multiply(dKm);
        BigDecimal timeFare = perMinute.multiply(dMin);
        BigDecimal subtotal = baseFare.add(distFare).add(timeFare);
        BigDecimal total    = subtotal.multiply(sMul).setScale(2, RoundingMode.HALF_UP);
        BigDecimal comm     = total.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payout   = total.subtract(comm).setScale(2, RoundingMode.HALF_UP);

        return new FareBreakdown(
                baseFare.setScale(2, RoundingMode.HALF_UP),
                distFare.setScale(2, RoundingMode.HALF_UP),
                timeFare.setScale(2, RoundingMode.HALF_UP),
                sMul.setScale(2, RoundingMode.HALF_UP),
                total,
                comm,
                payout
        );
    }

    public record FareBreakdown(
            BigDecimal baseFare,
            BigDecimal distanceFare,
            BigDecimal timeFare,
            BigDecimal surgeMultiplier,
            BigDecimal totalFare,
            BigDecimal commission,
            BigDecimal driverPayout
    ) {}
}
