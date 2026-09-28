package io.github.muslimqol.prayer;

import java.util.Locale;
import java.util.Optional;

/**
 * High-latitude rules for placing safe bounds on Fajr and Isha (and providing fallback times
 * when astronomical twilight depression angles are not reached during summer nights).
 *
 * <p>Matches standard Batoul Apps Adhan semantics:
 * <ul>
 *   <li>{@code FAJR} will never be earlier than {@code sunrise - nightDuration * fajrFraction}.</li>
 *   <li>{@code ISHA} will never be later than {@code sunset + nightDuration * ishaFraction}.</li>
 *   <li>{@link #NONE} disables high-latitude bounding and preserves the raw astronomical result
 *       (or {@link PrayerTimeSource#UNAVAILABLE} if astronomical twilight does not exist).</li>
 * </ul>
 */
public enum HighLatitudeRule {
    /**
     * Do not apply high-latitude safe bounds or fallback approximation; preserves astronomical
     * twilight when solvable, or {@link PrayerTimeSource#UNAVAILABLE} when unsolvable.
     */
    NONE,

    /**
     * Fajr will never be earlier than the middle of the night, and Isha will never be later than
     * the middle of the night (sunset to next sunrise fraction = 1/2).
     */
    MIDDLE_OF_NIGHT,

    /**
     * Fajr will never be earlier than the beginning of the last seventh of the night, and Isha
     * will never be later than the end of the first seventh of the night (fraction = 1/7).
     */
    SEVENTH_OF_NIGHT,

    /**
     * Similar to {@link #SEVENTH_OF_NIGHT}, but scales the safe night fraction proportionally to
     * the twilight depression angle: {@code fajrAngle / 60.0} and {@code ishaAngle / 60.0}.
     */
    TWILIGHT_ANGLE;

    /**
     * Returns the fraction of the night duration before sunrise used for the safe Fajr boundary.
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
     * Returns the fraction of the night duration after sunset used for the safe Isha boundary.
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
