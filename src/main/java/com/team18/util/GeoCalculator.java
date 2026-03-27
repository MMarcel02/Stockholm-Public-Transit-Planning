package com.team18.util;

public class GeoCalculator {
    private static final int EARTH_RADIUS = 6371;

    // This formula approximates the distance, its faster than the haversine one but less accurate over long distances
    // for our need (over the distance of a city) it should be relatively accurate, but we should measure and compare  
    // from: https://www.baeldung.com/java-find-distance-between-points
    public static double calculateEquirectangularDistance(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double lon1Rad = Math.toRadians(lon1);
        double lon2Rad = Math.toRadians(lon2);

        double x = (lon2Rad - lon1Rad) * Math.cos((lat1Rad + lat2Rad) / 2);
        double y = (lat2Rad - lat1Rad);
        double distance = Math.sqrt(x * x + y * y) * EARTH_RADIUS;

        return distance;
    }


    // Theoretically more accurate, but will be slower to perform because of expensive sin, cos, and arctan operations
    // Can verify this in in test1_results.jsonl by looking at the speedup and accuracy
    // from: https://www.baeldung.com/java-find-distance-between-points
    private static double haversine(double val) {
        return Math.pow(Math.sin(val / 2), 2);
    }

    public static double calculateHaversineDistance(double startLat, double startLong, double endLat, double endLong) {

        double dLat = Math.toRadians((endLat - startLat));
        double dLong = Math.toRadians((endLong - startLong));

        startLat = Math.toRadians(startLat);
        endLat = Math.toRadians(endLat);

        double a = haversine(dLat) + Math.cos(startLat) * Math.cos(endLat) * haversine(dLong);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }
}
