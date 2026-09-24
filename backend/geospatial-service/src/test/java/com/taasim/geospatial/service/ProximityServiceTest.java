package com.taasim.geospatial.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProximityServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private GeoOperations<String, String> geoOperations;

    private ProximityService proximityService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        proximityService = new ProximityService(redisTemplate);
    }

    @Test
    void updatePosition_callsGeoAddWithCorrectPoint() {
        proximityService.updatePosition("taxi_001", 33.5731, -7.5898);

        verify(geoOperations).add(
                eq(ProximityService.GEO_KEY),
                eq(new Point(-7.5898, 33.5731)),
                eq("taxi_001")
        );
    }

    @Test
    void removeDriver_callsGeoRemove() {
        proximityService.removeDriver("taxi_001");

        verify(geoOperations).remove(ProximityService.GEO_KEY, "taxi_001");
    }

    @Test
    void findNearby_returnsMappedDriverList() {
        RedisGeoCommands.GeoLocation<String> location = new RedisGeoCommands.GeoLocation<>(
                "taxi_001", new Point(-7.5898, 33.5731)
        );
        GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult = new GeoResult<>(
                location, new Distance(450.25, RedisGeoCommands.DistanceUnit.METERS)
        );
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = new GeoResults<>(List.of(geoResult));

        when(geoOperations.radius(eq(ProximityService.GEO_KEY), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(geoResults);

        List<Map<String, Object>> nearby = proximityService.findNearby(33.5731, -7.5898, 2000);

        assertThat(nearby).hasSize(1);
        assertThat(nearby.get(0).get("driverId")).isEqualTo("taxi_001");
        assertThat(nearby.get(0).get("distanceMeters")).isEqualTo(450.25);
        assertThat(nearby.get(0).get("lat")).isEqualTo(33.5731);
        assertThat(nearby.get(0).get("lon")).isEqualTo(-7.5898);
    }
}
