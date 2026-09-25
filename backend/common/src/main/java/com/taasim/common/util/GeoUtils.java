package com.taasim.common.util;

/**
 * Shared geospatial utility methods for Casablanca urban coordinates & zones.
 */
public final class GeoUtils {

    public static final double LON_MIN = -7.6895;
    public static final double LON_MAX = -7.4008;
    public static final double LAT_MIN = 33.5072;
    public static final double LAT_MAX = 33.6527;

    private GeoUtils() {}

    /**
     * Calculate the great-circle distance between two points on Earth using Haversine formula.
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
     * Calculate zone ID (1 to 16) from latitude and longitude.
     */
    public static int calculateZoneId(double lat, double lon) {
        int gridX = (int) (4 * (lon - LON_MIN) / (LON_MAX - LON_MIN));
        int gridY = (int) (4 * (lat - LAT_MIN) / (LAT_MAX - LAT_MIN));
        gridX = Math.max(0, Math.min(3, gridX));
        gridY = Math.max(0, Math.min(3, gridY));
        return (gridY * 4) + gridX + 1;
    }

    /**
     * Get the center coordinates [lat, lon] for a Casablanca zone (1 to 16).
     */
    public static double[] getZoneCenter(int zoneId) {
        int index = Math.max(0, Math.min(15, zoneId - 1));
        int gridX = index % 4;
        int gridY = index / 4;

        double lonStep = (LON_MAX - LON_MIN) / 4;
        double latStep = (LAT_MAX - LAT_MIN) / 4;

        double centerLon = LON_MIN + (gridX + 0.5) * lonStep;
        double centerLat = LAT_MIN + (gridY + 0.5) * latStep;

        return new double[]{centerLat, centerLon};
    }
}
