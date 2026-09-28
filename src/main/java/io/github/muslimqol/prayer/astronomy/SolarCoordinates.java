package io.github.muslimqol.prayer.astronomy;

/**
 * Immutable astronomical solar position parameters at a specific Julian Day instant.
 *
 * <p>Derived using the NOAA / Jean Meeus (Astronomical Algorithms, Ch. 25) solar position equations.
 *
 * @param julianDay                 Julian Day (JD)
 * @param julianCentury             Julian Century (T) from J2000.0 epoch (2451545.0)
 * @param geometricMeanLongitudeDeg solar geometric mean longitude (L0) in degrees [0, 360)
 * @param geometricMeanAnomalyDeg   solar geometric mean anomaly (M) in degrees [0, 360)
 * @param eccentricity              eccentricity of Earth's orbit (e)
 * @param equationOfCenterDeg       solar equation of the center (C) in degrees
 * @param apparentLongitudeDeg      solar apparent ecliptic longitude (lambda) in degrees
 * @param meanObliquityDeg          mean obliquity of the ecliptic (epsilon0) in degrees
 * @param apparentObliquityDeg      corrected apparent obliquity of the ecliptic (epsilon) in degrees
 * @param declinationDeg            solar declination (delta) in degrees [-90, +90]
 * @param equationOfTimeMinutes     equation of time (EoT) in minutes
 */
public record SolarCoordinates(
        double julianDay,
        double julianCentury,
        double geometricMeanLongitudeDeg,
        double geometricMeanAnomalyDeg,
        double eccentricity,
        double equationOfCenterDeg,
        double apparentLongitudeDeg,
        double meanObliquityDeg,
        double apparentObliquityDeg,
        double declinationDeg,
        double equationOfTimeMinutes
) {
}
