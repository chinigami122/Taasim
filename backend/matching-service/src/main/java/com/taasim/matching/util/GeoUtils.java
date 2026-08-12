package com.taasim.matching.util;

/**
 * Geospatial utility methods.
 * Ported from src/flink/trip_matcher_job.py lines 44-51.
 */
public final class GeoUtils {

    private GeoUtils() {}

    /**
     * Calculate the great-circle distance between two points on Earth.
     *
     * @return distance in meters
     */
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6_371_000; // Earth's radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Estimate ETA in seconds based on distance.
     * Assumes average speed of 30 km/h in city traffic.
     *
     * @param distanceMeters distance in meters
     * @return estimated seconds, minimum 30
     */
    public static int computeEta(double distanceMeters) {
        double avgSpeedMs = 30_000.0 / 3600; // 30 km/h → m/s
        return Math.max(30, (int) (distanceMeters / avgSpeedMs));
    }

    /**
     * Get the center coordinates for a Casablanca zone.
     * Grid: 4×4 over the city bounds.
     *
     * LON: -7.6895 to -7.4008
     * LAT: 33.5072 to 33.6527
     */
    public static double[] getZoneCenter(int zoneId) {
        double LON_MIN = -7.6895, LON_MAX = -7.4008;
        double LAT_MIN = 33.5072, LAT_MAX = 33.6527;

        int index = zoneId - 1; // 0-indexed
        int gridX = index % 4;
        int gridY = index / 4;

        double lonStep = (LON_MAX - LON_MIN) / 4;
        double latStep = (LAT_MAX - LAT_MIN) / 4;

        double centerLon = LON_MIN + (gridX + 0.5) * lonStep;
        double centerLat = LAT_MIN + (gridY + 0.5) * latStep;

        return new double[]{centerLat, centerLon};
    }
}