package io.github.muslimqol.prayer;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable calculated result for a single {@link Prayer} event on a civil day.
 *
 * <p>Unavailable events are represented explicitly via {@link Optional#empty()} and {@link PrayerTimeSource#UNAVAILABLE},
 * never via sentinel timestamps such as epoch zero or midnight.
 *
 * @param prayer  the prayer or solar event
 * @param instant resolved UTC instant if available, or empty if unavailable
 * @param source  provenance source of the timestamp
 */
public record PrayerMoment(
        Prayer prayer,
        Optional<Instant> instant,
        PrayerTimeSource source
) {

    public PrayerMoment {
        Objects.requireNonNull(prayer, "prayer must not be null");
        Objects.requireNonNull(instant, "instant optional must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (source == PrayerTimeSource.UNAVAILABLE) {
            if (instant.isPresent()) {
                throw new IllegalArgumentException("UNAVAILABLE moment must not contain an Instant: " + instant);
            }
        } else {
            if (instant.isEmpty()) {
                throw new IllegalArgumentException("Available source " + source + " must contain a non-empty Instant");
            }
        }
    }

    public static PrayerMoment astronomical(Prayer prayer, Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        return new PrayerMoment(prayer, Optional.of(instant), PrayerTimeSource.ASTRONOMICAL);
    }

    public static PrayerMoment highLatitudeAdjusted(Prayer prayer, Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        return new PrayerMoment(prayer, Optional.of(instant), PrayerTimeSource.HIGH_LATITUDE_ADJUSTED);
    }

    public static PrayerMoment fixedInterval(Prayer prayer, Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        return new PrayerMoment(prayer, Optional.of(instant), PrayerTimeSource.FIXED_INTERVAL);
    }

    public static PrayerMoment unavailable(Prayer prayer) {
        return new PrayerMoment(prayer, Optional.empty(), PrayerTimeSource.UNAVAILABLE);
    }

    /**
     * Returns true if this event has a resolved timestamp.
     */
    public boolean isAvailable() {
        return instant.isPresent();
    }

    /**
     * Returns a new {@link PrayerMoment} shifted by the given minute adjustment if available,
     * or returns this instance unchanged if unavailable or if {@code minutes == 0}.
     */
    public PrayerMoment withMinuteAdjustment(int minutes) {
        if (minutes == 0 || instant.isEmpty()) {
            return this;
        }
        return new PrayerMoment(prayer, Optional.of(instant.get().plus(Duration.ofMinutes(minutes))), source);
    }
}
