package io.github.muslimqol.client.qibla;

import io.github.muslimqol.config.ClientConfig;
import io.github.muslimqol.qibla.BearingMath;
import io.github.muslimqol.qibla.GeoCoordinate;
import io.github.muslimqol.qibla.QiblaCalculator;
import io.github.muslimqol.qibla.QiblaResult;

/**
 * Client-side service managing observer location evaluation, bearing caching, and relative angle calculation.
 *
 * <p>Observer coordinates remain strictly local on the client and are never sent over the network or logged.
 */
public final class QiblaClientService {

    private static boolean lastEnabled = false;
    private static boolean lastConfigured = false;
    private static double lastLatitude = Double.NaN;
    private static double lastLongitude = Double.NaN;
    private static QiblaResult cachedResult = null;

    private QiblaClientService() {}

    /**
     * Resets internal cached state. Useful for test environments or config resets.
     */
    public static synchronized void resetCache() {
        lastEnabled = false;
        lastConfigured = false;
        lastLatitude = Double.NaN;
        lastLongitude = Double.NaN;
        cachedResult = null;
    }

    /**
     * Retrieves the cached {@link QiblaResult} based on client configuration, re-evaluating only
     * when configuration parameters change.
     *
     * @return active {@link QiblaResult}, or null if feature is disabled or location is not configured
     */
    public static synchronized QiblaResult getCachedQiblaResult() {
        if (ClientConfig.SPEC == null || !ClientConfig.SPEC.isLoaded()) {
            return null;
        }

        boolean enabled = ClientConfig.QIBLA_ENABLED.get();
        boolean configured = ClientConfig.QIBLA_LOCATION_CONFIGURED.get();
        double lat = ClientConfig.QIBLA_LATITUDE.get();
        double lon = ClientConfig.QIBLA_LONGITUDE.get();

        if (!enabled || !configured) {
            cachedResult = null;
            lastEnabled = enabled;
            lastConfigured = configured;
            lastLatitude = lat;
            lastLongitude = lon;
            return null;
        }

        if (cachedResult == null
                || enabled != lastEnabled
                || configured != lastConfigured
                || Double.compare(lat, lastLatitude) != 0
                || Double.compare(lon, lastLongitude) != 0) {
            lastEnabled = enabled;
            lastConfigured = configured;
            lastLatitude = lat;
            lastLongitude = lon;
            try {
                GeoCoordinate observer = GeoCoordinate.of(lat, lon);
                cachedResult = QiblaCalculator.calculate(observer);
            } catch (IllegalArgumentException e) {
                cachedResult = null;
            }
        }

        return cachedResult;
    }

    /**
     * Computes the immutable render state from a {@link QiblaResult} and player yaw.
     *
     * @param result       calculated Qibla result
     * @param playerYawDeg player's current or interpolated yaw in degrees
     * @param toleranceDeg alignment tolerance in degrees
     * @return computed {@link QiblaClientState}
     */
    public static QiblaClientState computeState(QiblaResult result, float playerYawDeg, double toleranceDeg) {
        if (result == null || !result.defined()) {
            return QiblaClientState.UNAVAILABLE;
        }

        double playerHeading = BearingMath.playerYawToHeading(playerYawDeg);
        double relativeAngle = BearingMath.calculateRelativeAngle(result.bearingDeg(), playerHeading);
        boolean aligned = BearingMath.isAligned(relativeAngle, toleranceDeg);

        return QiblaClientState.of(result.bearingDeg(), playerHeading, relativeAngle, aligned);
    }

    /**
     * Convenience method to get current client state using cached config and default alignment tolerance.
     *
     * @param playerYawDeg player yaw in degrees
     * @return current {@link QiblaClientState}
     */
    public static QiblaClientState getCurrentState(float playerYawDeg) {
        return computeState(getCachedQiblaResult(), playerYawDeg, BearingMath.DEFAULT_ALIGNED_TOLERANCE_DEG);
    }
}
