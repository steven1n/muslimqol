package io.github.muslimqol.qibla;

/**
 * Pure mathematical utilities for angle normalization, Minecraft yaw conversion, and relative heading calculations.
 */
public final class BearingMath {

    /**
     * Default angular tolerance for facing Qibla in degrees.
     */
    public static final double DEFAULT_ALIGNED_TOLERANCE_DEG = 3.0;

    private BearingMath() {}

    /**
     * Normalizes any angle in degrees into the half-open range [0.0, 360.0).
     *
     * <p>Examples:
     * <ul>
     *   <li>normalize360(-10.0) -> 350.0</li>
     *   <li>normalize360(370.0) -> 10.0</li>
     *   <li>normalize360(360.0) -> 0.0</li>
     * </ul>
     */
    public static double normalize360(double angleDeg) {
        if (!Double.isFinite(angleDeg)) {
            throw new IllegalArgumentException("Angle must be finite: " + angleDeg);
        }
        double normalized = angleDeg % 360.0;
        if (normalized < 0.0) {
            normalized += 360.0;
        }
        if (normalized >= 360.0 || normalized == -0.0) {
            normalized = 0.0;
        }
        return normalized;
    }

    /**
     * Normalizes any angle in degrees into the signed range [-180.0, 180.0).
     *
     * <p>Examples:
     * <ul>
     *   <li>normalizeSigned(350.0) -> -10.0</li>
     *   <li>normalizeSigned(190.0) -> -170.0</li>
     *   <li>normalizeSigned(-190.0) -> 170.0</li>
     *   <li>normalizeSigned(180.0) -> -180.0</li>
     * </ul>
     */
    public static double normalizeSigned(double angleDeg) {
        double normalized = normalize360(angleDeg);
        if (normalized >= 180.0) {
            normalized -= 360.0;
        }
        return normalized;
    }

    /**
     * Converts a Minecraft player yaw angle to a geographic-style heading in degrees [0, 360).
     *
     * <p>Minecraft convention:
     * <ul>
     *   <li>yaw 0°    = South (+Z) -> heading 180°</li>
     *   <li>yaw 90°   = West  (-X) -> heading 270°</li>
     *   <li>yaw -90°  = East  (+X) -> heading 90°</li>
     *   <li>yaw ±180° = North (-Z) -> heading 0°</li>
     * </ul>
     *
     * <p>Formula: {@code headingDeg = normalize360(180.0 + playerYawDeg)}
     */
    public static double playerYawToHeading(double playerYawDeg) {
        return normalize360(180.0 + playerYawDeg);
    }

    /**
     * Calculates the signed relative angle from the player's heading to the Qibla bearing.
     *
     * <p>Convention:
     * <ul>
     *   <li>0° = player faces directly toward Qibla</li>
     *   <li>positive (+1° to +179°) = Qibla is to the player's right</li>
     *   <li>negative (-1° to -179°) = Qibla is to the player's left</li>
     *   <li>-180° = Qibla is directly behind the player</li>
     * </ul>
     */
    public static double calculateRelativeAngle(double qiblaBearingDeg, double playerHeadingDeg) {
        return normalizeSigned(qiblaBearingDeg - playerHeadingDeg);
    }

    /**
     * Determines whether the relative angle falls within the specified alignment tolerance.
     */
    public static boolean isAligned(double relativeAngleDeg, double toleranceDeg) {
        if (!Double.isFinite(toleranceDeg) || toleranceDeg < 0.0) {
            throw new IllegalArgumentException("Tolerance must be finite and non-negative: " + toleranceDeg);
        }
        return Math.abs(normalizeSigned(relativeAngleDeg)) <= toleranceDeg;
    }

    /**
     * Determines whether the relative angle falls within {@link #DEFAULT_ALIGNED_TOLERANCE_DEG} (3.0°).
     */
    public static boolean isAligned(double relativeAngleDeg) {
        return isAligned(relativeAngleDeg, DEFAULT_ALIGNED_TOLERANCE_DEG);
    }
}
