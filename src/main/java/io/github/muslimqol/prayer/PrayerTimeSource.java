package io.github.muslimqol.prayer;

/**
 * Provenance classification for a calculated {@link PrayerMoment}.
 */
public enum PrayerTimeSource {
    /**
     * Solved directly from the astronomical solar altitude / meridian transit equations.
     */
    ASTRONOMICAL,

    /**
     * Resolved via a configured high-latitude twilight rule when astronomical twilight does not occur.
     */
    HIGH_LATITUDE_ADJUSTED,

    /**
     * Resolved via a fixed minute interval offset (reserved for future interval-based calculation profiles).
     */
    FIXED_INTERVAL,

    /**
     * Event could not be determined (e.g. polar day/night or unavailable twilight with HighLatitudeRule.NONE).
     */
    UNAVAILABLE
}
