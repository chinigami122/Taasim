package com.taasim.matching.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GeoUtilsTest {

    @Test
    void haversine_sameCoordinates_returnsZero() {
        double d = GeoUtils.haversine(33.5731, -7.5898, 33.5731, -7.5898);
        assertThat(d).isEqualTo(0.0);
    }

    @Test
    void haversine_casablancaCenterToAinDiab_returnsAround6km() {
        // Casablanca center (Place Mohammed V) → Ain Diab
        double d = GeoUtils.haversine(33.5731, -7.5898, 33.5934, -7.6787);
        assertThat(d).isBetween(8_000.0, 9_500.0);  // meters
    }

    @Test
    void haversine_isSymmetric() {
        double a = GeoUtils.haversine(33.5, -7.5, 33.6, -7.6);
        double b = GeoUtils.haversine(33.6, -7.6, 33.5, -7.5);
        assertThat(a).isEqualTo(b);
    }
}