package io.github.muslimqol.salah;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable decision emitted by {@link ReminderEngine} when a reminder threshold is crossed.
 */
public record ReminderDecision(
        ReminderKey key,
        SalahEvent event,
        ReminderType type,
        Instant triggerInstant,
        int advanceMinutes
) {

    public ReminderDecision {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(triggerInstant, "triggerInstant must not be null");
        if (advanceMinutes < 0) {
            throw new IllegalArgumentException("advanceMinutes must be >= 0");
        }
    }
}
