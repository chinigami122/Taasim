package com.taasim.geospatial.service;

import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ProximityService {

    public static final String GEO_KEY = "drivers:available";
    private final StringRedisTemplate redisTemplate;

    public ProximityService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Add or update a driver's position in Redis GEO index */
    public void updatePosition(String driverId, double lat, double lon) {
        // Point in Spring Data Geo is Point(longitude, latitude)
        redisTemplate.opsForGeo().add(GEO_KEY, new Point(lon, lat), driverId);
    }

    /** Remove a driver from available index (e.g. went offline or on active trip) */
    public void removeDriver(String driverId) {
        redisTemplate.opsForGeo().remove(GEO_KEY, driverId);
    }

    /** Find drivers within radius meters of a coordinate, sorted by proximity */
    public List<Map<String, Object>> findNearby(double lat, double lon, double radiusMeters) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                redisTemplate.opsForGeo().radius(
                        GEO_KEY,
                        new Circle(new Point(lon, lat), new Distance(radiusMeters, RedisGeoCommands.DistanceUnit.METERS)),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()
                                .includeCoordinates()
                                .sortAscending()
                                .limit(50)
                );

        List<Map<String, Object>> nearby = new ArrayList<>();
        if (results != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
                if (result.getContent() != null && result.getContent().getPoint() != null) {
                    nearby.add(Map.of(
                            "driverId", result.getContent().getName(),
                            "distanceMeters", Math.round(result.getDistance().getValue() * 100.0) / 100.0,
                            "lat", result.getContent().getPoint().getY(),
                            "lon", result.getContent().getPoint().getX()
                    ));
                }
            }
        }
        return nearby;
    }
}
