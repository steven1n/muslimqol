package io.github.muslimqol.salah;

import io.github.muslimqol.prayer.Prayer;
import io.github.muslimqol.prayer.PrayerMoment;
import io.github.muslimqol.prayer.PrayerTimeSource;
import io.github.muslimqol.prayer.PrayerTimes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable representation of a resolved obligatory Salah event for a specific civil date.
 *
 * <p>Privacy invariant: Never stores or exposes observer geographic coordinates.
 *
 * <p>Scope invariant: Only obligatory prayers ({@link Prayer#isObligatoryPrayer()}) may be
 * represented as a {@code SalahEvent}; {@link Prayer#SUNRISE} is excluded.
 */
public record SalahEvent(
        Prayer prayer,
        Instant instant,
        ZonedDateTime zonedDateTime,
        PrayerTimeSource source,
        LocalDate civilDate
) {

    public SalahEvent {
        Objects.requireNonNull(prayer, "prayer must not be null");
        Objects.requireNonNull(instant, "instant must not be null");
        Objects.requireNonNull(zonedDateTime, "zonedDateTime must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(civilDate, "civilDate must not be null");

        if (!prayer.isObligatoryPrayer()) {
            throw new IllegalArgumentException("SalahEvent requires an obligatory prayer, got: " + prayer);
        }
        if (source == PrayerTimeSource.UNAVAILABLE) {
            throw new IllegalArgumentException("SalahEvent cannot be created with UNAVAILABLE source");
        }
    }

    /**
     * Extracts an available obligatory {@link SalahEvent} from a daily {@link PrayerTimes} schedule.
     */
    public static Optional<SalahEvent> fromSchedule(PrayerTimes schedule, Prayer prayer) {
        Objects.requireNonNull(schedule, "schedule must not be null");
        Objects.requireNonNull(prayer, "prayer must not be null");

        if (!prayer.isObligatoryPrayer()) {
            return Optional.empty();
        }

        PrayerMoment moment = schedule.moment(prayer);
        if (!moment.isAvailable()) {
            return Optional.empty();
        }

        Instant instant = moment.instant().orElseThrow();
        ZonedDateTime zdt = instant.atZone(schedule.zoneId());
        return Optional.of(new SalahEvent(
                prayer,
                instant,
                zdt,
                moment.source(),
                schedule.civilDate()
        ));
    }
}
