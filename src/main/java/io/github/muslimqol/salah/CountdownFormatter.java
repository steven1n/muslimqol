package io.github.muslimqol.salah;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure formatter and duration decomposer for Salah countdowns and local prayer times.
 *
 * <p>User-facing HUD rendering must use {@link #decompose(Duration)} ({@link CountdownValue})
 * together with localized translation keys rather than embedding {@link #formatCountdown(Duration)}
 * directly into non-English UI strings.
 */
public final class CountdownFormatter {

    private static final DateTimeFormatter LOCAL_TIME_HH_MM =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private CountdownFormatter() {}

    /**
     * Decomposes a remaining duration into a language-neutral {@link CountdownValue}
     * without seconds or locale-specific unit strings.
     */
    public static CountdownValue decompose(Duration remaining) {
        return CountdownValue.fromDuration(remaining);
    }

    /**
     * Formats a remaining duration into a deterministic ASCII string without seconds
     * (for diagnostics, logs, and pure unit tests). Never returns a negative value.
     */
    public static String formatCountdown(Duration remaining) {
        CountdownValue value = decompose(remaining);
        if (value.lessThanOneMinute()) {
            return "<1m";
        }
        if (value.hours() == 0L) {
            return value.minutes() + "m";
        }
        return String.format(Locale.ROOT, "%dh %02dm", value.hours(), value.minutes());
    }

    /**
     * Formats a prayer's zoned date-time in its configured {@code ZoneId} as {@code HH:mm}.
     */
    public static String formatLocalTime(ZonedDateTime zonedDateTime) {
        Objects.requireNonNull(zonedDateTime, "zonedDateTime must not be null");
        return LOCAL_TIME_HH_MM.format(zonedDateTime);
    }
}
