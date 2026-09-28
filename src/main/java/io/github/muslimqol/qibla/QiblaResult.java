package io.github.muslimqol.qibla;

import java.util.Objects;

/**
 * Result of a Qibla bearing calculation.
 *
 * @param defined    true if a valid bearing was determined, false if degenerate (e.g. at Kaaba or antipodal)
 * @param bearingDeg initial great-circle bearing in degrees [0, 360) where 0 = North, 90 = East, or NaN if undefined
 * @param reason     detailed reason code
 */
public record QiblaResult(
        boolean defined,
        double bearingDeg,
        QiblaResultReason reason
) {

    public QiblaResult {
        Objects.requireNonNull(reason, "reason must not be null");
        if (defined) {
            if (!Double.isFinite(bearingDeg) || bearingDeg < 0.0 || bearingDeg >= 360.0) {
                throw new IllegalArgumentException("Defined bearing must be finite and within [0, 360): " + bearingDeg);
            }
        } else {
            if (!Double.isNaN(bearingDeg)) {
                throw new IllegalArgumentException("Undefined result must specify NaN bearing: " + bearingDeg);
            }
        }
    }

    public static QiblaResult ok(double bearingDeg) {
        return new QiblaResult(true, bearingDeg, QiblaResultReason.OK);
    }

    public static QiblaResult atKaaba() {
        return new QiblaResult(false, Double.NaN, QiblaResultReason.AT_KAABA);
    }

    public static QiblaResult antipodal() {
        return new QiblaResult(false, Double.NaN, QiblaResultReason.ANTIPODAL);
    }
}
