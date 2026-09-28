package io.github.muslimqol.prayer;

import java.util.Locale;
import java.util.Optional;

/**
 * Parameterized angle-based calculation presets for Fajr and Isha solar depression angles.
 *
 * <p>These presets are standard astronomical software conventions and do not represent
 * religious certification or recommendation.
 *
 * <p>Note: Interval-based or seasonal methods such as {@code UMM_AL_QURA}, {@code QATAR},
 * and {@code MOONSIGHTING_COMMITTEE} are intentionally deferred to avoid shipping incomplete
 * angle-only approximations.
 */
public enum CalculationMethod {
    /**
     * Muslim World League (MWL): Fajr 18.0°, Isha 17.0°.
     */
    MUSLIM_WORLD_LEAGUE(18.0, 17.0),

    /**
     * Egyptian General Authority of Survey: Fajr 19.5°, Isha 17.5°.
     */
    EGYPTIAN(19.5, 17.5),

    /**
     * University of Islamic Sciences, Karachi: Fajr 18.0°, Isha 18.0°.
     */
    KARACHI(18.0, 18.0),

    /**
     * Islamic Society of North America (ISNA): Fajr 15.0°, Isha 15.0°.
     */
    NORTH_AMERICA(15.0, 15.0),

    /**
     * Kuwait: Fajr 18.0°, Isha 17.5°.
     */
    KUWAIT(18.0, 17.5),

    /**
     * Majlis Ugama Islam Singapura (MUIS): Fajr 20.0°, Isha 18.0°.
     */
    SINGAPORE(20.0, 18.0),

    /**
     * Dubai (UAE): Fajr 18.2°, Isha 18.2°.
     */
    DUBAI(18.2, 18.2),

    /**
     * User-specified custom Fajr and Isha solar depression angles.
     */
    CUSTOM(18.0, 17.0);

    private final double defaultFajrAngleDeg;
    private final double defaultIshaAngleDeg;

    CalculationMethod(double defaultFajrAngleDeg, double defaultIshaAngleDeg) {
        this.defaultFajrAngleDeg = defaultFajrAngleDeg;
        this.defaultIshaAngleDeg = defaultIshaAngleDeg;
    }

    public double defaultFajrAngleDeg() {
        return defaultFajrAngleDeg;
    }

    public double defaultIshaAngleDeg() {
        return defaultIshaAngleDeg;
    }

    /**
     * Parses a configuration string into a {@link CalculationMethod} without throwing on malformed input.
     */
    public static Optional<CalculationMethod> fromConfigName(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (CalculationMethod method : values()) {
            if (method.name().equals(normalized)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }
}
