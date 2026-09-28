package io.github.muslimqol;

import io.github.muslimqol.prayer.AsrMethod;
import io.github.muslimqol.prayer.CalculationMethod;
import io.github.muslimqol.prayer.HighLatitudeRule;
import io.github.muslimqol.prayer.Prayer;
import io.github.muslimqol.prayer.PrayerMoment;
import io.github.muslimqol.prayer.PrayerTimes;
import io.github.muslimqol.salah.CountdownFormatter;
import io.github.muslimqol.salah.SalahEvent;
import io.github.muslimqol.salah.SalahScheduleService;
import io.github.muslimqol.salah.SalahScheduleState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalahScheduleServiceTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 3, 19);
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 20);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 3, 21);

    private static PrayerTimes fixedSchedule(
            LocalDate date,
            ZoneId zone,
            String fajrHhMm,
            String sunriseHhMm,
            String dhuhrHhMm,
            String asrHhMm,
            String maghribHhMm,
            String ishaHhMm
    ) {
        Map<Prayer, PrayerMoment> moments = new EnumMap<>(Prayer.class);
        moments.put(Prayer.FAJR, moment(Prayer.FAJR, date, zone, fajrHhMm));
        moments.put(Prayer.SUNRISE, moment(Prayer.SUNRISE, date, zone, sunriseHhMm));
        moments.put(Prayer.DHUHR, moment(Prayer.DHUHR, date, zone, dhuhrHhMm));
        moments.put(Prayer.ASR, moment(Prayer.ASR, date, zone, asrHhMm));
        moments.put(Prayer.MAGHRIB, moment(Prayer.MAGHRIB, date, zone, maghribHhMm));
        moments.put(Prayer.ISHA, moment(Prayer.ISHA, date, zone, ishaHhMm));
        return new PrayerTimes(
                date,
                zone,
                CalculationMethod.MUSLIM_WORLD_LEAGUE,
                AsrMethod.STANDARD,
                HighLatitudeRule.MIDDLE_OF_NIGHT,
                moments
        );
    }

    private static PrayerMoment moment(Prayer prayer, LocalDate date, ZoneId zone, String hhMm) {
        if (hhMm == null) {
            return PrayerMoment.unavailable(prayer);
        }
        String[] parts = hhMm.split(":");
        ZonedDateTime zdt = date.atTime(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])).atZone(zone);
        return PrayerMoment.astronomical(prayer, zdt.toInstant());
    }

    private static Instant instantAt(LocalDate date, ZoneId zone, int hour, int minute) {
        return date.atTime(hour, minute).atZone(zone).toInstant();
    }

    @Test
    void resolvesNextObligatoryPrayerThroughoutDayAndNeverSelectsSunrise() {
        PrayerTimes yesterday = fixedSchedule(YESTERDAY, UTC, "05:12", "06:30", "12:15", "15:40", "18:10", "19:30");
        PrayerTimes today = fixedSchedule(TODAY, UTC, "05:10", "06:28", "12:15", "15:42", "18:12", "19:32");
        PrayerTimes tomorrow = fixedSchedule(TOMORROW, UTC, "05:08", "06:26", "12:14", "15:43", "18:14", "19:34");

        // 1. Before Fajr -> next = today Fajr, previous = yesterday Isha
        SalahScheduleState beforeFajr = SalahScheduleService.resolve(
                instantAt(TODAY, UTC, 4, 30),
                yesterday,
                today,
                tomorrow
        );
        assertEquals(Prayer.FAJR, beforeFajr.nextPrayer().orElseThrow().prayer());
        assertEquals(TODAY, beforeFajr.nextPrayer().orElseThrow().civilDate());
        assertEquals(Prayer.ISHA, beforeFajr.previousPrayer().orElseThrow().prayer());
        assertEquals(YESTERDAY, beforeFajr.previousPrayer().orElseThrow().civilDate());
        assertEquals(beforeFajr.previousPrayer(), beforeFajr.lastStartedPrayer());

        // 2. Between Fajr and Sunrise -> next = today Dhuhr (Sunrise is skipped!)
        SalahScheduleState beforeSunrise = SalahScheduleService.resolve(
                instantAt(TODAY, UTC, 6, 0),
                yesterday,
                today,
                tomorrow
        );
        assertEquals(Prayer.DHUHR, beforeSunrise.nextPrayer().orElseThrow().prayer());
        assertEquals(Prayer.FAJR, beforeSunrise.previousPrayer().orElseThrow().prayer());

        // 3. Between Sunrise and Dhuhr -> next = today Dhuhr, previous = today Fajr (NOT Sunrise)
        SalahScheduleState afterSunrise = SalahScheduleService.resolve(
                instantAt(TODAY, UTC, 8, 0),
                yesterday,
                today,
                tomorrow
        );
        assertEquals(Prayer.DHUHR, afterSunrise.nextPrayer().orElseThrow().prayer());
        assertEquals(Prayer.FAJR, afterSunrise.previousPrayer().orElseThrow().prayer());

        // 4. Between Dhuhr and Asr -> next = today Asr, previous = today Dhuhr
        SalahScheduleState afterDhuhr = SalahScheduleService.resolve(
                instantAt(TODAY, UTC, 13, 0),
                yesterday,
                today,
                tomorrow
        );
        assertEquals(Prayer.ASR, afterDhuhr.nextPrayer().orElseThrow().prayer());
        assertEquals(Prayer.DHUHR, afterDhuhr.previousPrayer().orElseThrow().prayer());

        // 5. After Maghrib before Isha -> next = today Isha, previous = today Maghrib
        SalahScheduleState afterMaghrib = SalahScheduleService.resolve(
                instantAt(TODAY, UTC, 18, 45),
                yesterday,
                today,
                tomorrow
        );
        assertEquals(Prayer.ISHA, afterMaghrib.nextPrayer().orElseThrow().prayer());
        assertEquals(Prayer.MAGHRIB, afterMaghrib.previousPrayer().orElseThrow().prayer());

        // 6. After Isha -> next = tomorrow Fajr, previous = today Isha
        SalahScheduleState afterIsha = SalahScheduleService.resolve(
                instantAt(TODAY, UTC, 23, 50),
                yesterday,
                today,
                tomorrow
        );
        SalahEvent nextFajr = afterIsha.nextPrayer().orElseThrow();
        assertEquals(Prayer.FAJR, nextFajr.prayer());
        assertEquals(TOMORROW, nextFajr.civilDate());
        assertEquals(Prayer.ISHA, afterIsha.previousPrayer().orElseThrow().prayer());
        assertEquals(TODAY, afterIsha.previousPrayer().orElseThrow().civilDate());
    }

    @Test
    void crossMidnightCountdownComputesExactDurationAcrossDays() {
        PrayerTimes today = fixedSchedule(TODAY, UTC, "05:10", "06:28", "12:15", "15:42", "18:12", "19:32");
        PrayerTimes tomorrow = fixedSchedule(TOMORROW, UTC, "05:10", "06:26", "12:14", "15:43", "18:14", "19:34");

        // 23:50 local -> tomorrow Fajr 05:10 -> 5h 20m
        Instant now = instantAt(TODAY, UTC, 23, 50);
        SalahScheduleState state = SalahScheduleService.resolve(now, today, tomorrow);

        Duration remaining = state.remainingUntilNext().orElseThrow();
        assertEquals(Duration.ofHours(5).plusMinutes(20), remaining);
        assertEquals("5h 20m", CountdownFormatter.formatCountdown(remaining));
        assertFalse(remaining.isNegative());
    }

    @Test
    void skipsUnavailableTomorrowFajrWithoutFabricatingFallback() {
        PrayerTimes today = fixedSchedule(TODAY, UTC, "05:10", "06:28", "12:15", "15:42", "18:12", "19:32");
        PrayerTimes tomorrowPolar = fixedSchedule(TOMORROW, UTC, null, null, "12:14", "15:43", null, null);

        Instant afterIsha = instantAt(TODAY, UTC, 22, 0);
        SalahScheduleState state = SalahScheduleService.resolve(afterIsha, today, tomorrowPolar);

        SalahEvent next = state.nextPrayer().orElseThrow();
        assertEquals(Prayer.DHUHR, next.prayer());
        assertEquals(TOMORROW, next.civilDate());
    }

    @Test
    void returnsStructuredUnavailableWhenAllSearchedEventsAreUnavailable() {
        PrayerTimes allUnavailableToday = fixedSchedule(TODAY, UTC, null, null, null, null, null, null);
        PrayerTimes allUnavailableTomorrow = fixedSchedule(TOMORROW, UTC, null, null, null, null, null, null);

        Instant now = instantAt(TODAY, UTC, 12, 0);
        SalahScheduleState state = SalahScheduleService.resolve(
                now,
                List.of(allUnavailableToday, allUnavailableTomorrow)
        );

        assertTrue(state.previousPrayer().isEmpty());
        assertTrue(state.nextPrayer().isEmpty());
        assertTrue(state.remainingUntilNext().isEmpty());
    }
}
