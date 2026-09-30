package io.github.muslimqol.salah;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure, language-neutral decomposition of a remaining countdown duration into hours, minutes,
 * and sub-minute status.
 *
 * <p>Contains zero Minecraft, NeoForge, or UI dependencies. Consumed by client presentation
 * layers to construct localized countdown text in English, Arabic, or other languages without
 * hard-coding English unit suffixes.
 */
public record CountdownValue(
        long hours,
        long minutes,
        boolean lessThanOneMinute
) {

    public CountdownValue {
        if (hours < 0) {
            throw new IllegalArgumentException("hours must be >= 0");
        }
        if (minutes < 0 || minutes >= 60) {
            throw new IllegalArgumentException("minutes must be in [0, 59]");
        }
        if (lessThanOneMinute && (hours != 0 || minutes != 0)) {
            throw new IllegalArgumentException("lessThanOneMinute requires hours == 0 and minutes == 0");
        }
    }

    /**
     * Decomposes a remaining duration into a non-negative {@link CountdownValue}.
     *
     * <ul>
     *   <li>Negative, zero, or positive durations under 60 seconds: {@code (0, 0, true)}</li>
     *   <li>60 seconds or more: {@code (totalMinutes / 60, totalMinutes % 60, false)}</li>
     * </ul>
     */
    public static CountdownValue fromDuration(Duration remaining) {
        Objects.requireNonNull(remaining, "remaining must not be null");
        if (remaining.isNegative() || remaining.isZero()) {
            return new CountdownValue(0L, 0L, true);
        }

        long totalSeconds = remaining.getSeconds();
        if (totalSeconds < 60L) {
            return new CountdownValue(0L, 0L, true);
        }

        long totalMinutes = totalSeconds / 60L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return new CountdownValue(hours, minutes, false);
    }

    /**
     * Returns the minute component formatted as a two-digit zero-padded string ({@code "00"}..{@code "59"}).
     */
    public String zeroPaddedMinutes() {
        return String.format(Locale.ROOT, "%02d", minutes);
    }
}
