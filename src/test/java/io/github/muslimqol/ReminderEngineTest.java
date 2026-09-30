package io.github.muslimqol;

import io.github.muslimqol.prayer.AsrMethod;
import io.github.muslimqol.prayer.CalculationMethod;
import io.github.muslimqol.prayer.HighLatitudeRule;
import io.github.muslimqol.prayer.Prayer;
import io.github.muslimqol.prayer.PrayerMoment;
import io.github.muslimqol.prayer.PrayerTimes;
import io.github.muslimqol.salah.ReminderDecision;
import io.github.muslimqol.salah.ReminderEngine;
import io.github.muslimqol.salah.ReminderKey;
import io.github.muslimqol.salah.ReminderType;
import io.github.muslimqol.salah.SalahNotificationPreferences;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderEngineTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final LocalDate DATE = LocalDate.of(2026, 3, 20);

    private static PrayerTimes sampleSchedule() {
        Map<Prayer, PrayerMoment> moments = new EnumMap<>(Prayer.class);
        moments.put(Prayer.FAJR, moment(Prayer.FAJR, "05:10"));
        moments.put(Prayer.SUNRISE, moment(Prayer.SUNRISE, "06:30"));
        moments.put(Prayer.DHUHR, moment(Prayer.DHUHR, "12:15"));
        moments.put(Prayer.ASR, moment(Prayer.ASR, "16:37"));
        moments.put(Prayer.MAGHRIB, moment(Prayer.MAGHRIB, "18:15"));
        moments.put(Prayer.ISHA, moment(Prayer.ISHA, "19:35"));
        return new PrayerTimes(
                DATE,
                UTC,
                CalculationMethod.MUSLIM_WORLD_LEAGUE,
                AsrMethod.STANDARD,
                HighLatitudeRule.MIDDLE_OF_NIGHT,
                moments
        );
    }

    private static PrayerMoment moment(Prayer prayer, String hhMm) {
        String[] parts = hhMm.split(":");
        ZonedDateTime zdt = DATE.atTime(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])).atZone(UTC);
        return PrayerMoment.astronomical(prayer, zdt.toInstant());
    }

    private static Instant at(String hhMmSs) {
        return Instant.parse("2026-03-20T" + hhMmSs + "Z");
    }

    @Test
    void upcomingReminderFiresOnCrossingAndDoesNotFireBeforeOrTwice() {
        ReminderEngine engine = new ReminderEngine();
        List<PrayerTimes> schedules = List.of(sampleSchedule());
        SalahNotificationPreferences prefs = SalahNotificationPreferences.defaults(); // 10m advance -> Asr upcoming at 16:27:00

        // First evaluation initializes watermark at 16:26:00
        assertTrue(engine.evaluate(at("16:26:00"), schedules, prefs).isEmpty());

        // Before crossing (16:26:59) -> does not fire
        assertTrue(engine.evaluate(at("16:26:59"), schedules, prefs).isEmpty());

        // Crossing 16:27:00 -> fires UPCOMING once
        List<ReminderDecision> triggered = engine.evaluate(at("16:27:00"), schedules, prefs);
        assertEquals(1, triggered.size());
        ReminderDecision decision = triggered.get(0);
        assertEquals(Prayer.ASR, decision.event().prayer());
        assertEquals(ReminderType.UPCOMING, decision.type());
        assertEquals(10, decision.advanceMinutes());
        assertEquals(at("16:27:00"), decision.triggerInstant());

        // Subsequent poll at 16:27:01 or re-evaluating across 16:27:00 -> does not fire twice
        assertTrue(engine.evaluate(at("16:27:01"), schedules, prefs).isEmpty());
        assertTrue(engine.evaluate(at("16:26:50"), at("16:27:05"), schedules, prefs).isEmpty());
    }

    @Test
    void startReminderFiresOnCrossingAndDoesNotFireTwice() {
        ReminderEngine engine = new ReminderEngine();
        List<PrayerTimes> schedules = List.of(sampleSchedule());
        SalahNotificationPreferences prefs = SalahNotificationPreferences.defaults();

        // Initialize watermark at 16:36:50
        assertTrue(engine.evaluate(at("16:36:50"), schedules, prefs).isEmpty());

        // Cross Asr start (16:37:00)
        List<ReminderDecision> triggered = engine.evaluate(at("16:37:02"), schedules, prefs);
        assertEquals(1, triggered.size());
        ReminderDecision decision = triggered.get(0);
        assertEquals(Prayer.ASR, decision.event().prayer());
        assertEquals(ReminderType.STARTED, decision.type());
        assertEquals(0, decision.advanceMinutes());
        assertEquals(at("16:37:00"), decision.triggerInstant());

        // Does not fire twice
        assertTrue(engine.evaluate(at("16:37:03"), schedules, prefs).isEmpty());
    }

    @Test
    void firstEvaluationDoesNotReplayPastReminders() {
        ReminderEngine engine = new ReminderEngine();
        List<PrayerTimes> schedules = List.of(sampleSchedule());
        SalahNotificationPreferences prefs = SalahNotificationPreferences.defaults();

        // Game starts at 17:00 after Fajr, Dhuhr, and Asr (16:37) have already passed
        List<ReminderDecision> firstPoll = engine.evaluate(at("17:00:00"), schedules, prefs);
        assertTrue(firstPoll.isEmpty());

        // Next poll at 17:00:01 also emits nothing
        assertTrue(engine.evaluate(at("17:00:01"), schedules, prefs).isEmpty());
    }

    @Test
    void smallForwardJumpWithinCatchUpWindowDeliversRecentReminder() {
        ReminderEngine engine = new ReminderEngine();
        List<PrayerTimes> schedules = List.of(sampleSchedule());
        SalahNotificationPreferences prefs = SalahNotificationPreferences.defaults();

        // Previous poll at 16:36:00, forward jump of 4 minutes to 16:40:00 (within 5m catch-up window of Asr 16:37:00)
        List<ReminderDecision> decisions = engine.evaluate(at("16:36:00"), at("16:40:00"), schedules, prefs);
        assertEquals(1, decisions.size());
        assertEquals(Prayer.ASR, decisions.get(0).event().prayer());
        assertEquals(ReminderType.STARTED, decisions.get(0).type());
    }

    @Test
    void largeForwardJumpBeyondCatchUpWindowDoesNotReplayStaleReminders() {
        ReminderEngine engine = new ReminderEngine();
        List<PrayerTimes> schedules = List.of(sampleSchedule());
        SalahNotificationPreferences prefs = SalahNotificationPreferences.defaults();

        // Previous poll at 16:36:00, jump of 12 minutes to 16:48:00 (> 5m catch-up window from Asr 16:37:00)
        List<ReminderDecision> decisions = engine.evaluate(at("16:36:00"), at("16:48:00"), schedules, prefs);
        assertTrue(decisions.isEmpty());

        // Multi-hour jump from 06:00:00 to 20:00:00 (> 5m from Isha 19:35:00) -> no pile of stale reminders
        List<ReminderDecision> multiHour = engine.evaluate(at("06:00:00"), at("20:00:00"), schedules, prefs);
        assertTrue(multiHour.isEmpty());
    }

    @Test
    void backwardClockJumpResetsWatermarkSafelyWithoutDuplicates() {
        ReminderEngine engine = new ReminderEngine();
        List<PrayerTimes> schedules = List.of(sampleSchedule());
        SalahNotificationPreferences prefs = SalahNotificationPreferences.defaults();

        // Initialize and cross Asr start at 16:37:00
        engine.evaluate(at("16:36:55"), schedules, prefs);
        assertEquals(1, engine.evaluate(at("16:37:05"), schedules, prefs).size());

        // OS clock moves backward to 16:36:50
        List<ReminderDecision> onBackwardJump = engine.evaluate(at("16:36:50"), schedules, prefs);
        assertTrue(onBackwardJump.isEmpty());
        assertEquals(at("16:36:50"), engine.previousPollInstant().orElseThrow());

        // Clock moves forward again across 16:37:00 -> already delivered, must NOT duplicate!
        assertTrue(engine.evaluate(at("16:37:05"), schedules, prefs).isEmpty());
    }

    @Test
    void disabledNotificationAndPerPrayerSwitchesSuppressReminders() {
        List<PrayerTimes> schedules = List.of(sampleSchedule());

        // 1. Master notificationsEnabled = false
        ReminderEngine engineMasterDisabled = new ReminderEngine();
        SalahNotificationPreferences masterDisabled = new SalahNotificationPreferences(
                true, true, false, true, 10, true, true, true, true, true, true
        );
        assertTrue(engineMasterDisabled.evaluate(at("16:26:50"), at("16:27:05"), schedules, masterDisabled).isEmpty());
        assertTrue(engineMasterDisabled.evaluate(at("16:36:50"), at("16:37:05"), schedules, masterDisabled).isEmpty());

        // 2. advanceNotificationMinutes = 0 disables advance reminder while keeping start reminder
        ReminderEngine engineZeroAdvance = new ReminderEngine();
        SalahNotificationPreferences zeroAdvance = new SalahNotificationPreferences(
                true, true, true, true, 0, true, true, true, true, true, true
        );
        assertTrue(engineZeroAdvance.evaluate(at("16:36:50"), at("16:37:05"), schedules, zeroAdvance)
                .stream().allMatch(d -> d.type() == ReminderType.STARTED));

        // 3. Individual prayer disabled (notifyAsr = false) suppresses Asr but allows Maghrib
        ReminderEngine engineAsrDisabled = new ReminderEngine();
        SalahNotificationPreferences asrDisabled = new SalahNotificationPreferences(
                true, true, true, true, 10, true, true, true, false, true, true
        );
        assertTrue(engineAsrDisabled.evaluate(at("16:26:50"), at("16:27:05"), schedules, asrDisabled).isEmpty());
        assertTrue(engineAsrDisabled.evaluate(at("16:36:50"), at("16:37:05"), schedules, asrDisabled).isEmpty());
        assertEquals(1, engineAsrDisabled.evaluate(at("18:14:50"), at("18:15:05"), schedules, asrDisabled).size());
    }

    @Test
    void sunriseNeverProducesSalahReminderAndIsRejectedByReminderKey() {
        ReminderEngine engine = new ReminderEngine();
        List<PrayerTimes> schedules = List.of(sampleSchedule()); // Sunrise is at 06:30
        SalahNotificationPreferences prefs = SalahNotificationPreferences.defaults();

        // Cross both 06:20 (10m before Sunrise) and 06:30 (Sunrise itself)
        assertTrue(engine.evaluate(at("06:19:00"), at("06:21:00"), schedules, prefs).isEmpty());
        assertTrue(engine.evaluate(at("06:29:00"), at("06:31:00"), schedules, prefs).isEmpty());

        assertThrows(
                IllegalArgumentException.class,
                () -> new ReminderKey(DATE, Prayer.SUNRISE, ReminderType.STARTED, at("06:30:00"))
        );
    }

    @Test
    void unavailablePrayerMomentNeverProducesReminderAndDeliveredSetRemainsBounded() {
        Map<Prayer, PrayerMoment> moments = new EnumMap<>(Prayer.class);
        moments.put(Prayer.FAJR, PrayerMoment.unavailable(Prayer.FAJR));
        moments.put(Prayer.SUNRISE, PrayerMoment.unavailable(Prayer.SUNRISE));
        moments.put(Prayer.DHUHR, moment(Prayer.DHUHR, "12:15"));
        moments.put(Prayer.ASR, moment(Prayer.ASR, "16:37"));
        moments.put(Prayer.MAGHRIB, PrayerMoment.unavailable(Prayer.MAGHRIB));
        moments.put(Prayer.ISHA, PrayerMoment.unavailable(Prayer.ISHA));
        PrayerTimes partialSchedule = new PrayerTimes(
                DATE, UTC, CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD, HighLatitudeRule.NONE, moments
        );

        ReminderEngine engine = new ReminderEngine();
        SalahNotificationPreferences prefs = SalahNotificationPreferences.defaults();

        // Cross 05:10 -> Fajr is unavailable so nothing fires
        assertTrue(engine.evaluate(at("05:09:00"), at("05:11:00"), List.of(partialSchedule), prefs).isEmpty());

        // Populate more than MAX_DELIVERED_KEYS across synthetic days to verify bounded retention
        for (int i = 0; i < 300; i++) {
            LocalDate day = DATE.plusDays(i);
            ZonedDateTime dhuhrZdt = day.atTime(12, 15).atZone(UTC);
            Map<Prayer, PrayerMoment> dayMoments = new EnumMap<>(Prayer.class);
            dayMoments.put(Prayer.FAJR, PrayerMoment.unavailable(Prayer.FAJR));
            dayMoments.put(Prayer.SUNRISE, PrayerMoment.unavailable(Prayer.SUNRISE));
            dayMoments.put(Prayer.DHUHR, PrayerMoment.astronomical(Prayer.DHUHR, dhuhrZdt.toInstant()));
            dayMoments.put(Prayer.ASR, PrayerMoment.unavailable(Prayer.ASR));
            dayMoments.put(Prayer.MAGHRIB, PrayerMoment.unavailable(Prayer.MAGHRIB));
            dayMoments.put(Prayer.ISHA, PrayerMoment.unavailable(Prayer.ISHA));
            PrayerTimes daySchedule = new PrayerTimes(
                    day, UTC, CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD, HighLatitudeRule.NONE, dayMoments
            );
            engine.evaluate(
                    dhuhrZdt.toInstant().minusSeconds(10),
                    dhuhrZdt.toInstant().plusSeconds(1),
                    List.of(daySchedule),
                    prefs
            );
        }

        assertEquals(ReminderEngine.MAX_DELIVERED_KEYS, engine.deliveredKeys().size());
    }
}
