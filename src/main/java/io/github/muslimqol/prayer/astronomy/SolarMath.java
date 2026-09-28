package io.github.muslimqol.prayer.astronomy;

import io.github.muslimqol.qibla.BearingMath;
import io.github.muslimqol.qibla.GeoCoordinate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Pure deterministic astronomical solar calculator based on NOAA / Jean Meeus
 * (Astronomical Algorithms, 2nd Ed., Ch. 25) equations.
 *
 * <p>Contains zero Minecraft or NeoForge dependencies and performs zero network I/O.
 */
public final class SolarMath {

    /**
     * Conventional apparent solar altitude for sea-level sunrise and sunset,
     * accounting for standard atmospheric refraction (~34') and solar semi-diameter (~16').
     */
    public static final double APPARENT_SUNRISE_SUNSET_ALTITUDE_DEG = -0.833;

    /**
     * Julian Day of the J2000.0 epoch (2000-01-01 12:00:00 TT/UT).
     */
    public static final double J2000_JULIAN_DAY = 2451545.0;

    /**
     * Number of days in a Julian century.
     */
    public static final double DAYS_PER_JULIAN_CENTURY = 36525.0;

    private static final double NANOS_PER_HOUR = 3_600_000_000_000.0;
    private static final double DENOM_EPSILON = 1e-12;

    private SolarMath() {}

    /**
     * Computes the Julian Day at 00:00 UT for the given Gregorian date using the Meeus algorithm.
     */
    public static double toJulianDay(int year, int month, int day) {
        int y = year;
        int m = month;
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        int a = Math.floorDiv(y, 100);
        int b = 2 - a + Math.floorDiv(a, 4);
        return Math.floor(365.25 * (y + 4716))
                + Math.floor(30.6001 * (m + 1))
                + day + b - 1524.5;
    }

    /**
     * Computes the Julian Day at 00:00 UT for the given {@link LocalDate}.
     */
    public static double toJulianDay(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return toJulianDay(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    /**
     * Calculates solar position parameters and equation of time for a given Julian Day.
     *
     * @param julianDay Julian Day (including fractional UT day)
     * @return immutable {@link SolarCoordinates}
     */
    public static SolarCoordinates calculateSolarCoordinates(double julianDay) {
        if (!Double.isFinite(julianDay)) {
            throw new IllegalArgumentException("julianDay must be finite: " + julianDay);
        }

        double t = (julianDay - J2000_JULIAN_DAY) / DAYS_PER_JULIAN_CENTURY;

        // Geometric mean longitude of the Sun (L0), degrees [0, 360)
        double l0 = BearingMath.normalize360(280.46646 + t * (36000.76983 + t * 0.0003032));

        // Geometric mean anomaly of the Sun (M), degrees [0, 360)
        double m = BearingMath.normalize360(357.52911 + t * (35999.05029 - 0.0001537 * t));

        // Eccentricity of Earth's orbit (e)
        double e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);

        // Sun's equation of the center (C), degrees
        double mRad = Math.toRadians(m);
        double c = Math.sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t))
                + Math.sin(2.0 * mRad) * (0.019993 - 0.000101 * t)
                + Math.sin(3.0 * mRad) * 0.000289;

        // Sun's true geometric longitude
        double trueLongitude = l0 + c;

        // Longitude of the ascending node of the Moon's mean orbit (Omega)
        double omega = 125.04 - 1934.136 * t;
        double omegaRad = Math.toRadians(omega);

        // Sun's apparent longitude (lambda), corrected for nutation and aberration
        double apparentLongitude = trueLongitude - 0.00569 - 0.00478 * Math.sin(omegaRad);

        // Mean obliquity of the ecliptic (epsilon0), degrees
        double meanObliquity = 23.0 + (26.0 + ((21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813)))) / 60.0) / 60.0;

        // Corrected apparent obliquity (epsilon), degrees
        double apparentObliquity = meanObliquity + 0.00256 * Math.cos(omegaRad);

        // Solar declination (delta), degrees
        double epsRad = Math.toRadians(apparentObliquity);
        double lambdaRad = Math.toRadians(apparentLongitude);
        double sinDecl = Math.sin(epsRad) * Math.sin(lambdaRad);
        double declination = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, sinDecl))));

        // Equation of Time (EoT), minutes
        double y = Math.tan(epsRad / 2.0);
        y *= y;
        double l0Rad = Math.toRadians(l0);
        double eotRad = y * Math.sin(2.0 * l0Rad)
                - 2.0 * e * Math.sin(mRad)
                + 4.0 * e * y * Math.sin(mRad) * Math.cos(2.0 * l0Rad)
                - 0.5 * y * y * Math.sin(4.0 * l0Rad)
                - 1.25 * e * e * Math.sin(2.0 * mRad);
        double equationOfTimeMinutes = 4.0 * Math.toDegrees(eotRad);

        return new SolarCoordinates(
                julianDay,
                t,
                l0,
                m,
                e,
                c,
                apparentLongitude,
                meanObliquity,
                apparentObliquity,
                declination,
                equationOfTimeMinutes
        );
    }

    /**
     * Calculates the local solar hour angle (in degrees [0, 180]) for a requested solar altitude.
     *
     * @param latitudeDeg    observer latitude in degrees
     * @param declinationDeg solar declination in degrees
     * @param altitudeDeg    target solar center altitude in degrees
     * @return hour angle in degrees, or {@link OptionalDouble#empty()} if the sun does not reach that altitude
     */
    public static OptionalDouble calculateHourAngleDeg(double latitudeDeg, double declinationDeg, double altitudeDeg) {
        double latRad = Math.toRadians(latitudeDeg);
        double declRad = Math.toRadians(declinationDeg);
        double altRad = Math.toRadians(altitudeDeg);

        double denom = Math.cos(latRad) * Math.cos(declRad);
        if (Math.abs(denom) < DENOM_EPSILON) {
            return OptionalDouble.empty();
        }

        double cosH = (Math.sin(altRad) - Math.sin(latRad) * Math.sin(declRad)) / denom;
        if (!Double.isFinite(cosH) || cosH < -1.0 || cosH > 1.0) {
            return OptionalDouble.empty();
        }

        return OptionalDouble.of(Math.toDegrees(Math.acos(cosH)));
    }

    /**
     * Calculates the solar altitude angle (in degrees) at the start of Asr prayer.
     *
     * <p>Formula: {@code A = atan(1 / (shadowFactor + tan(|latitude - declination|)))}
     *
     * @param latitudeDeg    observer latitude in degrees
     * @param declinationDeg solar declination in degrees
     * @param shadowFactor   shadow length multiplier (1 for Standard, 2 for Hanafi)
     * @return Asr solar altitude in degrees
     */
    public static double calculateAsrAltitudeDeg(double latitudeDeg, double declinationDeg, int shadowFactor) {
        if (shadowFactor < 1) {
            throw new IllegalArgumentException("shadowFactor must be >= 1: " + shadowFactor);
        }
        double zenithAngleDeg = Math.abs(latitudeDeg - declinationDeg);
        double altRad = Math.atan(1.0 / (shadowFactor + Math.tan(Math.toRadians(zenithAngleDeg))));
        return Math.toDegrees(altRad);
    }

    /**
     * Calculates local solar noon (transit / Dhuhr) in UTC hours relative to 00:00:00 UTC of {@code civilDate}.
     *
     * <p>Note: For eastern longitudes (e.g., Asia), UTC hours may be small or negative; for western longitudes
     * (e.g., Americas), UTC hours may exceed 12.0. This preserves exact civil-date anchoring.
     */
    public static double calculateSolarNoonUtcHours(LocalDate civilDate, GeoCoordinate coordinate) {
        Objects.requireNonNull(civilDate, "civilDate must not be null");
        Objects.requireNonNull(coordinate, "coordinate must not be null");

        double jd0 = toJulianDay(civilDate);
        double baseNoonHours = 12.0 - coordinate.longitudeDeg() / 15.0;
        double noonUtcHours = baseNoonHours;

        for (int i = 0; i < 2; i++) {
            SolarCoordinates coords = calculateSolarCoordinates(jd0 + noonUtcHours / 24.0);
            noonUtcHours = baseNoonHours - coords.equationOfTimeMinutes() / 60.0;
        }
        return noonUtcHours;
    }

    /**
     * Solves for the UTC hours (relative to 00:00:00 UTC of {@code civilDate}) at which the sun crosses
     * {@code altitudeDeg} either before solar noon ({@code morning = true}) or after solar noon ({@code morning = false}).
     *
     * @param civilDate   observer's local civil date
     * @param coordinate  observer's geographic coordinates
     * @param altitudeDeg target solar altitude in degrees
     * @param morning     true for rising/morning event (before solar noon), false for setting/evening event (after solar noon)
     * @return UTC hours relative to {@code civilDate} 00:00:00Z, or empty if the sun does not cross {@code altitudeDeg}
     */
    public static OptionalDouble solarTimeForAltitude(
            LocalDate civilDate,
            GeoCoordinate coordinate,
            double altitudeDeg,
            boolean morning
    ) {
        Objects.requireNonNull(civilDate, "civilDate must not be null");
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        if (!Double.isFinite(altitudeDeg)) {
            throw new IllegalArgumentException("altitudeDeg must be finite: " + altitudeDeg);
        }

        double jd0 = toJulianDay(civilDate);
        double baseNoonHours = 12.0 - coordinate.longitudeDeg() / 15.0;
        double estimateHours = calculateSolarNoonUtcHours(civilDate, coordinate);

        for (int i = 0; i < 2; i++) {
            SolarCoordinates coords = calculateSolarCoordinates(jd0 + estimateHours / 24.0);
            OptionalDouble haOpt = calculateHourAngleDeg(coordinate.latitudeDeg(), coords.declinationDeg(), altitudeDeg);
            if (haOpt.isEmpty()) {
                return OptionalDouble.empty();
            }
            double haHours = haOpt.getAsDouble() / 15.0;
            double refinedNoon = baseNoonHours - coords.equationOfTimeMinutes() / 60.0;
            estimateHours = morning ? (refinedNoon - haHours) : (refinedNoon + haHours);
        }

        return OptionalDouble.of(estimateHours);
    }

    /**
     * Solves for the afternoon Asr time in UTC hours (relative to 00:00:00 UTC of {@code civilDate})
     * for the given shadow factor.
     */
    public static OptionalDouble solarTimeForAsr(
            LocalDate civilDate,
            GeoCoordinate coordinate,
            int shadowFactor
    ) {
        Objects.requireNonNull(civilDate, "civilDate must not be null");
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        if (shadowFactor < 1) {
            throw new IllegalArgumentException("shadowFactor must be >= 1: " + shadowFactor);
        }

        double jd0 = toJulianDay(civilDate);
        double baseNoonHours = 12.0 - coordinate.longitudeDeg() / 15.0;
        double estimateHours = calculateSolarNoonUtcHours(civilDate, coordinate);

        for (int i = 0; i < 2; i++) {
            SolarCoordinates coords = calculateSolarCoordinates(jd0 + estimateHours / 24.0);
            double meridianZenithDeg = Math.abs(coordinate.latitudeDeg() - coords.declinationDeg());
            if (meridianZenithDeg >= 90.0) {
                // Sun does not rise above geometric horizon at solar noon (polar night)
                return OptionalDouble.empty();
            }
            double asrAltitudeDeg = calculateAsrAltitudeDeg(coordinate.latitudeDeg(), coords.declinationDeg(), shadowFactor);
            OptionalDouble haOpt = calculateHourAngleDeg(coordinate.latitudeDeg(), coords.declinationDeg(), asrAltitudeDeg);
            if (haOpt.isEmpty()) {
                return OptionalDouble.empty();
            }
            double haHours = haOpt.getAsDouble() / 15.0;
            double refinedNoon = baseNoonHours - coords.equationOfTimeMinutes() / 60.0;
            estimateHours = refinedNoon + haHours;
        }

        return OptionalDouble.of(estimateHours);
    }

    /**
     * Converts UTC hours relative to 00:00:00 UTC of {@code civilDate} into a full-precision {@link Instant}.
     */
    public static Instant utcHoursToInstant(LocalDate civilDate, double utcHours) {
        Objects.requireNonNull(civilDate, "civilDate must not be null");
        if (!Double.isFinite(utcHours)) {
            throw new IllegalArgumentException("utcHours must be finite: " + utcHours);
        }
        Instant midnightUtc = civilDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        long offsetNanos = Math.round(utcHours * NANOS_PER_HOUR);
        return midnightUtc.plusNanos(offsetNanos);
    }
}
