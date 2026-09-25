package com.taasim.matching.util;

import com.taasim.common.util.GeoUtils;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GeoUtilsTest {

    @Test
    void haversine_sameCoordinates_returnsZero() {
        double d = GeoUtils.haversine(33.5731, -7.5898, 33.5731, -7.5898);
        assertThat(d).isEqualTo(0.0);
    }

    @Test
    void haversine_casablancaCenterToAinDiab_returnsAround8To9km() {
        double d = GeoUtils.haversine(33.5731, -7.5898, 33.5934, -7.6787);
        assertThat(d).isBetween(8_000.0, 9_500.0);
    }

    @Test
    void haversine_isSymmetric() {
        double a = GeoUtils.haversine(33.5, -7.5, 33.6, -7.6);
        double b = GeoUtils.haversine(33.6, -7.6, 33.5, -7.5);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void calculateZoneId_returnsValidZone1To16() {
        int zone = GeoUtils.calculateZoneId(33.5731, -7.5898);
        assertThat(zone).isBetween(1, 16);
    }

    @Test
    void getZoneCenter_returnsCoordinatesWithinCasablancaBounds() {
        double[] center = GeoUtils.getZoneCenter(5);
        assertThat(center).hasSize(2);
        assertThat(center[0]).isBetween(GeoUtils.LAT_MIN, GeoUtils.LAT_MAX);
        assertThat(center[1]).isBetween(GeoUtils.LON_MIN, GeoUtils.LON_MAX);
    }
}