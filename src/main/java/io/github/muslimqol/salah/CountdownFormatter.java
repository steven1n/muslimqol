package io.github.muslimqol.salah;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure formatter for Salah countdown durations and local prayer times.
 *
 * <p>Formatting rules:
 * <ul>
 *   <li>Negative or zero durations, and positive durations under 60 seconds: {@code "<1m"}</li>
 *   <li>1 to 59 minutes: {@code "1m"} .. {@code "59m"}</li>
 *   <li>60 minutes or more: {@code "1h 00m"}, {@code "2h 05m"}, {@code "24h 00m"}</li>
 *   <li>Seconds are never displayed in HUD countdowns.</li>
 * </ul>
 */
public final class CountdownFormatter {

    private static final DateTimeFormatter LOCAL_TIME_HH_MM =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private CountdownFormatter() {}

    /**
     * Formats a remaining duration into a stable human-readable countdown string without seconds.
     * Never returns a negative value.
     */
    public static String formatCountdown(Duration remaining) {
        Objects.requireNonNull(remaining, "remaining must not be null");
        if (remaining.isNegative() || remaining.isZero()) {
            return "<1m";
        }

        long totalSeconds = remaining.getSeconds();
        if (totalSeconds < 60L) {
            return "<1m";
        }

        long totalMinutes = totalSeconds / 60L;
        if (totalMinutes < 60L) {
            return totalMinutes + "m";
        }

        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return String.format(Locale.ROOT, "%dh %02dm", hours, minutes);
    }

    /**
     * Formats a prayer's zoned date-time in its configured {@code ZoneId} as {@code HH:mm}.
     */
    public static String formatLocalTime(ZonedDateTime zonedDateTime) {
        Objects.requireNonNull(zonedDateTime, "zonedDateTime must not be null");
        return LOCAL_TIME_HH_MM.format(zonedDateTime);
    }
}
