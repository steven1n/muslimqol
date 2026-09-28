package io.github.muslimqol.prayer;

import java.util.Locale;
import java.util.Optional;

/**
 * Fallback adjustment rules for high-latitude locations where astronomical Fajr or Isha
 * twilight depression angles are not reached during summer nights.
 */
public enum HighLatitudeRule {
    /**
     * Do not apply fallback approximation; astronomically unresolvable twilight remains {@link PrayerTimeSource#UNAVAILABLE}.
     */
    NONE,

    /**
     * Divide night duration (sunset to next sunrise) in half (fraction = 1/2).
     */
    MIDDLE_OF_NIGHT,

    /**
     * Allocate one-seventh of the night duration (fraction = 1/7).
     */
    SEVENTH_OF_NIGHT,

    /**
     * Scale night fraction proportionally to the twilight angle: {@code angle / 60.0}.
     */
    TWILIGHT_ANGLE;

    /**
     * Returns the fraction of the night duration before sunrise used for Fajr fallback.
     */
    public double fajrNightFraction(double fajrAngleDeg) {
        return switch (this) {
            case NONE -> 0.0;
            case MIDDLE_OF_NIGHT -> 0.5;
            case SEVENTH_OF_NIGHT -> 1.0 / 7.0;
            case TWILIGHT_ANGLE -> fajrAngleDeg / 60.0;
        };
    }

    /**
     * Returns the fraction of the night duration after sunset used for Isha fallback.
     */
    public double ishaNightFraction(double ishaAngleDeg) {
        return switch (this) {
            case NONE -> 0.0;
            case MIDDLE_OF_NIGHT -> 0.5;
            case SEVENTH_OF_NIGHT -> 1.0 / 7.0;
            case TWILIGHT_ANGLE -> ishaAngleDeg / 60.0;
        };
    }

    /**
     * Parses a configuration string into a {@link HighLatitudeRule} without throwing on malformed input.
     */
    public static Optional<HighLatitudeRule> fromConfigName(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (HighLatitudeRule rule : values()) {
            if (rule.name().equals(normalized)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }
}
