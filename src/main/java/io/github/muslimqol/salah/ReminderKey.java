package io.github.muslimqol.salah;

import io.github.muslimqol.prayer.Prayer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Unique identity for a Salah reminder occurrence within a client session.
 *
 * <p>Includes {@code prayerInstant} so that if schedule parameters or coordinates change,
 * newly computed prayer times do not collide with stale keys from a previous schedule.
 */
public record ReminderKey(
        LocalDate civilDate,
        Prayer prayer,
        ReminderType type,
        Instant prayerInstant
) {

    public ReminderKey {
        Objects.requireNonNull(civilDate, "civilDate must not be null");
        Objects.requireNonNull(prayer, "prayer must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(prayerInstant, "prayerInstant must not be null");
        if (!prayer.isObligatoryPrayer()) {
            throw new IllegalArgumentException("Non-obligatory event cannot be a Salah reminder target: " + prayer);
        }
    }
}
