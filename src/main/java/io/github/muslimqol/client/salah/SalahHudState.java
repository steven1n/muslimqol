package io.github.muslimqol.client.salah;

import io.github.muslimqol.prayer.Prayer;
import io.github.muslimqol.salah.CountdownFormatter;
import io.github.muslimqol.salah.CountdownValue;
import io.github.muslimqol.salah.SalahEvent;
import io.github.muslimqol.salah.SalahScheduleState;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Immutable render model for the client-only Salah HUD indicator.
 *
 * <p>Prepared on client tick / polling cadence by {@link SalahClientService} and consumed
 * directly by {@link SalahHudOverlay} without triggering astronomical recalculations inside
 * render callbacks. Retains language-neutral {@link Duration} and {@link CountdownValue}
 * rather than a pre-rendered English string so the HUD renders localized units in every language.
 */
public record SalahHudState(
        boolean visible,
        Prayer nextPrayer,
        ZonedDateTime localPrayerTime,
        String formattedLocalTime,
        Duration remaining,
        CountdownValue countdown
) {

    private static final SalahHudState HIDDEN = new SalahHudState(
            false,
            null,
            null,
            "",
            Duration.ZERO,
            new CountdownValue(0L, 0L, true)
    );

    public SalahHudState {
        if (visible) {
            Objects.requireNonNull(nextPrayer, "nextPrayer must not be null when visible");
            Objects.requireNonNull(localPrayerTime, "localPrayerTime must not be null when visible");
            Objects.requireNonNull(formattedLocalTime, "formattedLocalTime must not be null when visible");
            Objects.requireNonNull(remaining, "remaining must not be null when visible");
            Objects.requireNonNull(countdown, "countdown must not be null when visible");
            if (!nextPrayer.isObligatoryPrayer()) {
                throw new IllegalArgumentException("Salah HUD may only display obligatory prayers: " + nextPrayer);
            }
        }
    }

    /**
     * Returns the shared hidden HUD state instance.
     */
    public static SalahHudState hidden() {
        return HIDDEN;
    }

    /**
     * Derives an immutable {@link SalahHudState} from a resolved {@link SalahScheduleState}
     * and the HUD visibility configuration flag.
     */
    public static SalahHudState fromScheduleState(boolean hudEnabled, SalahScheduleState scheduleState) {
        if (!hudEnabled || scheduleState == null || scheduleState.nextPrayer().isEmpty()) {
            return HIDDEN;
        }

        SalahEvent next = scheduleState.nextPrayer().get();
        Duration remaining = scheduleState.remainingUntilNext().orElse(Duration.ZERO);
        return new SalahHudState(
                true,
                next.prayer(),
                next.zonedDateTime(),
                CountdownFormatter.formatLocalTime(next.zonedDateTime()),
                remaining,
                CountdownFormatter.decompose(remaining)
        );
    }
}
