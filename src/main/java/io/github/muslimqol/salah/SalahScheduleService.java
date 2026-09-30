package io.github.muslimqol.salah;

import io.github.muslimqol.prayer.Prayer;
import io.github.muslimqol.prayer.PrayerTimes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure, Minecraft-independent service that resolves the most recently started obligatory prayer
 * ({@code previousPrayer} / {@code lastStartedPrayer}) and the next upcoming obligatory prayer
 * ({@code nextPrayer}) across adjacent daily {@link PrayerTimes} schedules.
 *
 * <p>Strictly excludes non-obligatory events ({@link Prayer#SUNRISE}) and skips unavailable moments
 * ({@code PrayerTimeSource.UNAVAILABLE}) without fabricating fallback timestamps.
 */
public final class SalahScheduleService {

    /**
     * The five obligatory daily prayers in canonical daily order.
     */
    public static final List<Prayer> OBLIGATORY_PRAYERS = Arrays.stream(Prayer.values())
            .filter(Prayer::isObligatoryPrayer)
            .toList();

    private SalahScheduleService() {}

    /**
     * Resolves {@link SalahScheduleState} given {@code now}, {@code today}, and {@code tomorrow} schedules.
     */
    public static SalahScheduleState resolve(
            Instant now,
            PrayerTimes today,
            PrayerTimes tomorrow
    ) {
        Objects.requireNonNull(now, "now must not be null");
        List<PrayerTimes> schedules = new ArrayList<>(2);
        if (today != null) {
            schedules.add(today);
        }
        if (tomorrow != null) {
            schedules.add(tomorrow);
        }
        return resolve(now, schedules);
    }

    /**
     * Resolves {@link SalahScheduleState} given {@code now}, {@code yesterday}, {@code today},
     * and {@code tomorrow} schedules.
     */
    public static SalahScheduleState resolve(
            Instant now,
            PrayerTimes yesterday,
            PrayerTimes today,
            PrayerTimes tomorrow
    ) {
        Objects.requireNonNull(now, "now must not be null");
        List<PrayerTimes> schedules = new ArrayList<>(3);
        if (yesterday != null) {
            schedules.add(yesterday);
        }
        if (today != null) {
            schedules.add(today);
        }
        if (tomorrow != null) {
            schedules.add(tomorrow);
        }
        return resolve(now, schedules);
    }

    /**
     * Resolves {@link SalahScheduleState} across an arbitrary collection of daily schedules.
     *
     * <ul>
     *   <li>{@code previousPrayer}: latest available obligatory event with {@code instant <= now}.</li>
     *   <li>{@code nextPrayer}: earliest available obligatory event with {@code instant > now}.</li>
     * </ul>
     */
    public static SalahScheduleState resolve(Instant now, List<PrayerTimes> schedules) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(schedules, "schedules must not be null");

        List<SalahEvent> availableObligatoryEvents = new ArrayList<>();
        for (PrayerTimes schedule : schedules) {
            if (schedule == null) {
                continue;
            }
            for (Prayer prayer : OBLIGATORY_PRAYERS) {
                SalahEvent.fromSchedule(schedule, prayer).ifPresent(availableObligatoryEvents::add);
            }
        }

        availableObligatoryEvents.sort(Comparator.comparing(SalahEvent::instant));

        SalahEvent previous = null;
        SalahEvent next = null;

        for (SalahEvent candidate : availableObligatoryEvents) {
            if (!candidate.instant().isAfter(now)) {
                previous = candidate;
            } else if (next == null) {
                next = candidate;
            }
        }

        return new SalahScheduleState(
                now,
                Optional.ofNullable(previous),
                Optional.ofNullable(next)
        );
    }
}
