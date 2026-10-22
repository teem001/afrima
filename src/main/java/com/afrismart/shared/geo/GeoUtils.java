package com.afrismart.shared.geo;

public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoUtils() {
    }

    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static BoundingBox boundingBox(double lat, double lng, double radiusMeters) {
        double latDelta = Math.toDegrees(radiusMeters / EARTH_RADIUS_METERS);
        double cosLat = Math.cos(Math.toRadians(lat));
        double lngDelta = cosLat < 1e-6 ? 180 : Math.toDegrees(radiusMeters / (EARTH_RADIUS_METERS * cosLat));
        return new BoundingBox(lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta);
    }

    public static double roundKm(double meters) {
        return Math.round(meters / 100.0) / 10.0;
    }

    public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
    }
}
