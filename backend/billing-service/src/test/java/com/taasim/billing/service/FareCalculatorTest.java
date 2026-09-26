package com.taasim.billing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FareCalculatorTest {

    private FareCalculator calc;

    @BeforeEach
    void setUp() throws Exception {
        calc = new FareCalculator();
        setField(calc, "baseFare", new BigDecimal("5.00"));
        setField(calc, "perKm", new BigDecimal("3.50"));
        setField(calc, "perMinute", new BigDecimal("0.50"));
        setField(calc, "commissionRate", new BigDecimal("0.15"));
    }

    @Test
    void calculate_standardTrip_noSurge() {
        var breakdown = calc.calculate(5.0, 10.0, 1.0);
        // Base: 5.00
        // Distance: 5.0 * 3.50 = 17.50
        // Time: 10.0 * 0.50 = 5.00
        // Total: (5.00 + 17.50 + 5.00) * 1.0 = 27.50
        // Commission (15%): 27.50 * 0.15 = 4.125 -> 4.13
        // Driver Payout: 27.50 - 4.13 = 23.37
        assertThat(breakdown.totalFare().doubleValue()).isCloseTo(27.50, within(0.01));
        assertThat(breakdown.commission().doubleValue()).isCloseTo(4.13, within(0.01));
        assertThat(breakdown.driverPayout().doubleValue()).isCloseTo(23.37, within(0.01));
    }

    @Test
    void calculate_withSurgeMultiplier() {
        var breakdown = calc.calculate(5.0, 10.0, 2.0);
        // Subtotal = 27.50
        // Total = 27.50 * 2.0 = 55.00
        // Commission (15%) = 8.25
        // Driver Payout = 46.75
        assertThat(breakdown.totalFare().doubleValue()).isCloseTo(55.00, within(0.01));
        assertThat(breakdown.commission().doubleValue()).isCloseTo(8.25, within(0.01));
        assertThat(breakdown.driverPayout().doubleValue()).isCloseTo(46.75, within(0.01));
    }

    @Test
    void calculate_shortTrip_minimumDistanceAndTime() {
        var breakdown = calc.calculate(0.5, 2.0, 1.0);
        // Base: 5.00
        // Distance: 0.5 * 3.50 = 1.75
        // Time: 2.0 * 0.50 = 1.00
        // Subtotal: 7.75
        // Total: 7.75
        // Commission: 7.75 * 0.15 = 1.1625 -> 1.16
        // Payout: 6.59
        assertThat(breakdown.totalFare().doubleValue()).isCloseTo(7.75, within(0.01));
        assertThat(breakdown.commission().doubleValue()).isCloseTo(1.16, within(0.01));
        assertThat(breakdown.driverPayout().doubleValue()).isCloseTo(6.59, within(0.01));
    }

    @Test
    void calculate_longTrip_withHighSurge() {
        var breakdown = calc.calculate(25.0, 45.0, 1.8);
        // Base: 5.00
        // Distance: 25.0 * 3.50 = 87.50
        // Time: 45.0 * 0.50 = 22.50
        // Subtotal: 115.00
        // Total: 115.00 * 1.8 = 207.00
        // Commission: 207.00 * 0.15 = 31.05
        // Payout: 175.95
        assertThat(breakdown.totalFare().doubleValue()).isCloseTo(207.00, within(0.01));
        assertThat(breakdown.commission().doubleValue()).isCloseTo(31.05, within(0.01));
        assertThat(breakdown.driverPayout().doubleValue()).isCloseTo(175.95, within(0.01));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
