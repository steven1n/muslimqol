package io.github.muslimqol.qibla;

/**
 * Immutable geographic coordinate representing latitude and longitude on Earth in decimal degrees.
 *
 * <p>Valid ranges:
 * <ul>
 *   <li>Latitude: [-90.0, +90.0] (positive = North, negative = South)</li>
 *   <li>Longitude: [-180.0, +180.0] (positive = East, negative = West)</li>
 * </ul>
 */
public record GeoCoordinate(double latitudeDeg, double longitudeDeg) {

    public GeoCoordinate {
        if (!Double.isFinite(latitudeDeg) || !Double.isFinite(longitudeDeg)) {
            throw new IllegalArgumentException("Coordinates must be finite numbers: lat=" + latitudeDeg + ", lon=" + longitudeDeg);
        }
        if (latitudeDeg < -90.0 || latitudeDeg > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90.0 and +90.0 degrees: " + latitudeDeg);
        }
        if (longitudeDeg < -180.0 || longitudeDeg > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180.0 and +180.0 degrees: " + longitudeDeg);
        }
    }

    public static GeoCoordinate of(double latitudeDeg, double longitudeDeg) {
        return new GeoCoordinate(latitudeDeg, longitudeDeg);
    }
}
