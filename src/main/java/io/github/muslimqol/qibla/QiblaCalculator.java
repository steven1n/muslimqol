package io.github.muslimqol.qibla;

import java.util.Objects;

/**
 * Pure mathematical calculator for great-circle initial Qibla bearing from an observer coordinate on Earth to the Kaaba.
 *
 * <p>Contains no Minecraft-specific dependencies and performs zero networking or I/O.
 */
public final class QiblaCalculator {

    /**
     * Kaaba latitude in decimal degrees (21.4225° N).
     */
    public static final double KAABA_LATITUDE_DEG = 21.4225;

    /**
     * Kaaba longitude in decimal degrees (39.8262° E).
     */
    public static final double KAABA_LONGITUDE_DEG = 39.8262;

    /**
     * Centralized Kaaba reference coordinate.
     */
    public static final GeoCoordinate KAABA_COORDINATE = new GeoCoordinate(KAABA_LATITUDE_DEG, KAABA_LONGITUDE_DEG);

    private static final double EPSILON_DEG = 1e-6;
    private static final double EPSILON_VEC = 1e-9;

    private QiblaCalculator() {}

    /**
     * Calculates the initial great-circle bearing from the observer coordinate to the Kaaba.
     *
     * @param observer immutable observer coordinate
     * @return {@link QiblaResult} indicating bearing in degrees [0, 360) or reason for undefined result
     */
    public static QiblaResult calculate(GeoCoordinate observer) {
        Objects.requireNonNull(observer, "observer must not be null");

        if (isAtKaaba(observer)) {
            return QiblaResult.atKaaba();
        }
        if (isAntipodal(observer)) {
            return QiblaResult.antipodal();
        }

        double phi1 = Math.toRadians(observer.latitudeDeg());
        double phi2 = Math.toRadians(KAABA_LATITUDE_DEG);
        double deltaLambda = Math.toRadians(KAABA_LONGITUDE_DEG - observer.longitudeDeg());

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);

        if (Math.hypot(x, y) < EPSILON_VEC) {
            if (Math.abs(observer.latitudeDeg() - KAABA_LATITUDE_DEG) < 1.0) {
                return QiblaResult.atKaaba();
            } else {
                return QiblaResult.antipodal();
            }
        }

        double theta = Math.atan2(y, x);
        double bearingDeg = BearingMath.normalize360(Math.toDegrees(theta));
        return QiblaResult.ok(bearingDeg);
    }

    /**
     * Checks if the observer coordinate is coincident with the Kaaba within numeric tolerance.
     */
    public static boolean isAtKaaba(GeoCoordinate observer) {
        Objects.requireNonNull(observer, "observer must not be null");
        double dLat = Math.abs(observer.latitudeDeg() - KAABA_LATITUDE_DEG);
        double dLon = Math.abs(BearingMath.normalizeSigned(observer.longitudeDeg() - KAABA_LONGITUDE_DEG));
        return dLat < EPSILON_DEG && dLon < EPSILON_DEG;
    }

    /**
     * Checks if the observer coordinate is antipodal to the Kaaba within numeric tolerance.
     */
    public static boolean isAntipodal(GeoCoordinate observer) {
        Objects.requireNonNull(observer, "observer must not be null");
        double dLat = Math.abs(observer.latitudeDeg() - (-KAABA_LATITUDE_DEG));
        double dLon = Math.abs(Math.abs(BearingMath.normalizeSigned(observer.longitudeDeg() - KAABA_LONGITUDE_DEG)) - 180.0);
        return dLat < EPSILON_DEG && dLon < EPSILON_DEG;
    }
}
