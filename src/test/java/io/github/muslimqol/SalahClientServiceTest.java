package io.github.muslimqol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.muslimqol.client.salah.SalahClientService;
import io.github.muslimqol.client.salah.SalahHudOverlay;
import io.github.muslimqol.client.salah.SalahHudState;
import io.github.muslimqol.prayer.AsrMethod;
import io.github.muslimqol.prayer.CalculationMethod;
import io.github.muslimqol.prayer.HighLatitudeRule;
import io.github.muslimqol.prayer.Prayer;
import io.github.muslimqol.prayer.PrayerAdjustments;
import io.github.muslimqol.prayer.PrayerCalculationParameters;
import io.github.muslimqol.prayer.PrayerTimes;
import io.github.muslimqol.prayer.PrayerTimesCalculator;
import io.github.muslimqol.qibla.GeoCoordinate;
import io.github.muslimqol.salah.ReminderDecision;
import io.github.muslimqol.salah.ReminderType;
import io.github.muslimqol.salah.SalahEvent;
import io.github.muslimqol.salah.SalahNotificationPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalahClientServiceTest {

    private final List<ReminderDecision> dispatchedReminders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        SalahClientService.resetState();
        SalahClientService.setReminderDispatcher(dispatchedReminders::add);
        dispatchedReminders.clear();
    }

    @AfterEach
    void tearDown() {
        SalahClientService.resetState();
    }

    private SalahClientService.PollEvaluationResult evaluateAt(
            Instant instant,
            ZoneId zone,
            double lat,
            double lon,
            boolean hudEnabled,
            SalahNotificationPreferences prefs
    ) {
        return SalahClientService.evaluate(
                hudEnabled,
                prefs,
                lat,
                lon,
                zone.getId(),
                "MUSLIM_WORLD_LEAGUE",
                18.0,
                17.0,
                "STANDARD",
                "MIDDLE_OF_NIGHT",
                0, 0, 0, 0, 0, 0,
                Clock.fixed(instant, zone)
        );
    }

    @Test
    void midnightRolloverRefreshesCivilDateKeepsTomorrowFajrCoherentAndDoesNotDuplicateReminders() {
        ZoneId londonZone = ZoneId.of("Europe/London");
        double lat = 51.5074;
        double lon = -0.1278;
        SalahNotificationPreferences prefs = SalahNotificationPreferences.defaults();

        // 1. Before midnight on 2026-03-20 at 23:50Z (after Isha)
        Instant beforeMidnight = Instant.parse("2026-03-20T23:50:00Z");
        SalahClientService.PollEvaluationResult resBeforeMidnight = evaluateAt(
                beforeMidnight, londonZone, lat, lon, true, prefs
        );
        SalahEvent nextBeforeMidnight = resBeforeMidnight.scheduleState().nextPrayer().orElseThrow();
        assertEquals(Prayer.FAJR, nextBeforeMidnight.prayer());
        assertEquals(LocalDate.of(2026, 3, 21), nextBeforeMidnight.civilDate());
        assertTrue(dispatchedReminders.isEmpty());

        // 2. Advance clock beyond midnight to 2026-03-21T00:05:00Z
        Instant afterMidnight = Instant.parse("2026-03-21T00:05:00Z");
        SalahClientService.PollEvaluationResult resAfterMidnight = evaluateAt(
                afterMidnight, londonZone, lat, lon, true, prefs
        );
        SalahEvent nextAfterMidnight = resAfterMidnight.scheduleState().nextPrayer().orElseThrow();
        assertEquals(Prayer.FAJR, nextAfterMidnight.prayer());
        assertEquals(LocalDate.of(2026, 3, 21), nextAfterMidnight.civilDate());
        assertEquals(nextBeforeMidnight.instant(), nextAfterMidnight.instant());

        // 3. Advance to just before Fajr start and cross Fajr start once -> fires once, never duplicates
        Instant fajrInstant = nextAfterMidnight.instant();
        evaluateAt(fajrInstant.minusSeconds(10), londonZone, lat, lon, true, prefs);
        dispatchedReminders.clear();

        evaluateAt(fajrInstant.plusSeconds(1), londonZone, lat, lon, true, prefs);
        assertEquals(1, dispatchedReminders.size());
        assertEquals(Prayer.FAJR, dispatchedReminders.get(0).event().prayer());
        assertEquals(ReminderType.STARTED, dispatchedReminders.get(0).type());

        // Re-evaluating at fajrInstant + 2s does not duplicate
        evaluateAt(fajrInstant.plusSeconds(2), londonZone, lat, lon, true, prefs);
        assertEquals(1, dispatchedReminders.size());
    }

    @Test
    void dstSpringForwardAndFallBackCountdownsUseTrueInstantsAcrossLondonAndNewYork() {
        SalahNotificationPreferences prefs = SalahNotificationPreferences.defaults();

        // 1. Europe/London spring-forward on 2026-03-29 (clocks jump 01:00 GMT -> 02:00 BST)
        ZoneId london = ZoneId.of("Europe/London");
        Instant londonNightBefore = ZonedDateTime.of(2026, 3, 28, 23, 30, 0, 0, london).toInstant();
        SalahClientService.PollEvaluationResult londonSpring = evaluateAt(
                londonNightBefore, london, 51.5074, -0.1278, true, prefs
        );
        SalahEvent londonFajr = londonSpring.scheduleState().nextPrayer().orElseThrow();
        assertEquals(Prayer.FAJR, londonFajr.prayer());
        assertEquals("+01:00", londonFajr.zonedDateTime().getOffset().getId());
        assertEquals(
                Duration.between(londonNightBefore, londonFajr.instant()),
                londonSpring.hudState().remaining()
        );

        // 2. America/New_York spring-forward on 2026-03-08 (clocks jump 02:00 EST -> 03:00 EDT)
        ZoneId newYork = ZoneId.of("America/New_York");
        Instant nyNightBefore = ZonedDateTime.of(2026, 3, 7, 23, 30, 0, 0, newYork).toInstant();
        SalahClientService.PollEvaluationResult nySpring = evaluateAt(
                nyNightBefore, newYork, 40.7128, -74.0060, true, prefs
        );
        SalahEvent nyFajr = nySpring.scheduleState().nextPrayer().orElseThrow();
        assertEquals(Prayer.FAJR, nyFajr.prayer());
        assertEquals("-04:00", nyFajr.zonedDateTime().getOffset().getId());
        assertEquals(
                Duration.between(nyNightBefore, nyFajr.instant()),
                nySpring.hudState().remaining()
        );

        // 3. America/New_York fall-back on 2026-11-01 (clocks fall back 02:00 EDT -> 01:00 EST)
        Instant nyFallNightBefore = ZonedDateTime.of(2026, 10, 31, 23, 30, 0, 0, newYork).toInstant();
        SalahClientService.PollEvaluationResult nyFall = evaluateAt(
                nyFallNightBefore, newYork, 40.7128, -74.0060, true, prefs
        );
        SalahEvent nyFallFajr = nyFall.scheduleState().nextPrayer().orElseThrow();
        assertEquals(Prayer.FAJR, nyFallFajr.prayer());
        assertEquals("-05:00", nyFallFajr.zonedDateTime().getOffset().getId());
        assertEquals(
                Duration.between(nyFallNightBefore, nyFallFajr.instant()),
                nyFall.hudState().remaining()
        );
    }

    @Test
    void perPrayerSwitchDisablesAsrToastWhileKeepingAsrInScheduleAndHud() {
        ZoneId london = ZoneId.of("Europe/London");
        PrayerTimes schedule = PrayerTimesCalculator.calculate(
                GeoCoordinate.of(51.5074, -0.1278),
                LocalDate.of(2026, 3, 20),
                london,
                PrayerCalculationParameters.of(CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD, HighLatitudeRule.MIDDLE_OF_NIGHT, PrayerAdjustments.NONE)
        );
        Instant asrInstant = schedule.moment(Prayer.ASR).instant().orElseThrow();

        // Disable notifyAsr only
        SalahNotificationPreferences asrMuted = new SalahNotificationPreferences(
                true, true, true, true, 10, true,
                true, true, false, true, true
        );

        // 5 minutes before Asr -> Asr is still nextPrayer and shown on HUD
        SalahClientService.PollEvaluationResult beforeAsr = evaluateAt(
                asrInstant.minus(Duration.ofMinutes(5)), london, 51.5074, -0.1278, true, asrMuted
        );
        assertTrue(beforeAsr.hudState().visible());
        assertEquals(Prayer.ASR, beforeAsr.hudState().nextPrayer());

        // Cross Asr start -> no Toast dispatched for Asr, and nextPrayer rolls forward to Maghrib
        SalahClientService.PollEvaluationResult afterAsr = evaluateAt(
                asrInstant.plusSeconds(5), london, 51.5074, -0.1278, true, asrMuted
        );
        assertTrue(dispatchedReminders.isEmpty());
        assertEquals(Prayer.MAGHRIB, afterAsr.hudState().nextPrayer());
    }

    @Test
    void respectsPrayerEnabledAndSalahHudEnabledFlags() {
        ZoneId london = ZoneId.of("Europe/London");
        Instant noon = Instant.parse("2026-03-20T11:00:00Z");

        // prayer.enabled = false -> HUD hidden, schedule unavailable, no reminders
        SalahNotificationPreferences prayerDisabled = new SalahNotificationPreferences(
                false, true, true, true, 10, true,
                true, true, true, true, true
        );
        SalahClientService.PollEvaluationResult disabledRes = evaluateAt(
                noon, london, 51.5074, -0.1278, true, prayerDisabled
        );
        assertFalse(disabledRes.hudState().visible());
        assertTrue(disabledRes.scheduleState().nextPrayer().isEmpty());

        // salah_hud_enabled = false -> HUD hidden, but schedule state remains resolved
        SalahClientService.PollEvaluationResult hudDisabledRes = evaluateAt(
                noon, london, 51.5074, -0.1278, false, SalahNotificationPreferences.defaults()
        );
        assertFalse(hudDisabledRes.hudState().visible());
        assertTrue(hudDisabledRes.scheduleState().nextPrayer().isPresent());
    }

    @Test
    void hudLayoutCoexistencePositionsBelowQiblaOrStandalone() {
        assertEquals(28, SalahHudOverlay.resolvePosY(true));
        assertEquals(8, SalahHudOverlay.resolvePosY(false));
        assertEquals(SalahHudState.hidden(), SalahHudState.fromScheduleState(false, null));
    }

    @Test
    void scheduleConfigurationChangeResetsWatermarkWithoutRetroactiveAdvanceNotification() {
        ZoneId london = ZoneId.of("Europe/London");
        PrayerTimes londonSchedule = PrayerTimesCalculator.calculate(
                GeoCoordinate.of(51.5074, -0.1278),
                LocalDate.of(2026, 3, 20),
                london,
                PrayerCalculationParameters.of(CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD, HighLatitudeRule.MIDDLE_OF_NIGHT, PrayerAdjustments.NONE)
        );
        Instant londonAsr = londonSchedule.moment(Prayer.ASR).instant().orElseThrow();

        // 1. Poll in New York first
        Instant fiveMinBeforeLondonAsr = londonAsr.minus(Duration.ofMinutes(5));
        evaluateAt(fiveMinBeforeLondonAsr.minusSeconds(30), ZoneId.of("America/New_York"), 40.7128, -74.0060, true, SalahNotificationPreferences.defaults());
        assertTrue(dispatchedReminders.isEmpty());

        // 2. Switch coordinates/timezone to London when London Asr is only 5 minutes away (< 10m advance interval):
        //    must NOT retroactively emit the 10-minute advance notification on schedule switch!
        evaluateAt(fiveMinBeforeLondonAsr, london, 51.5074, -0.1278, true, SalahNotificationPreferences.defaults());
        assertTrue(dispatchedReminders.isEmpty());

        // 3. When London Asr actually starts 5 minutes later, STARTED reminder fires normally
        evaluateAt(londonAsr.plusSeconds(1), london, 51.5074, -0.1278, true, SalahNotificationPreferences.defaults());
        assertEquals(1, dispatchedReminders.size());
        assertEquals(Prayer.ASR, dispatchedReminders.get(0).event().prayer());
        assertEquals(ReminderType.STARTED, dispatchedReminders.get(0).type());
    }

    @Test
    void englishAndArabicLanguageFilesHaveCompleteKeyParityForSalah() throws Exception {
        JsonObject en = loadLangJson("/assets/muslimqol/lang/en_us.json");
        JsonObject ar = loadLangJson("/assets/muslimqol/lang/ar_sa.json");

        Set<String> enKeys = en.keySet();
        Set<String> arKeys = ar.keySet();
        assertEquals(enKeys, arKeys, "en_us.json and ar_sa.json must have identical key sets");

        List<String> requiredSalahKeys = List.of(
                "hud.muslimqol.salah.next",
                "hud.muslimqol.salah.countdown",
                "hud.muslimqol.salah.duration.hours_minutes",
                "hud.muslimqol.salah.duration.hours",
                "hud.muslimqol.salah.duration.minutes",
                "hud.muslimqol.salah.duration.less_than_minute",
                "notification.muslimqol.salah.upcoming.title",
                "notification.muslimqol.salah.upcoming.body",
                "notification.muslimqol.salah.started.title",
                "notification.muslimqol.salah.started.body",
                "config.muslimqol.prayer.salah_hud_enabled",
                "config.muslimqol.prayer.notifications_enabled",
                "config.muslimqol.prayer.advance_notification_enabled",
                "config.muslimqol.prayer.advance_notification_minutes",
                "config.muslimqol.prayer.start_notification_enabled",
                "config.muslimqol.prayer.notify_fajr",
                "config.muslimqol.prayer.notify_dhuhr",
                "config.muslimqol.prayer.notify_asr",
                "config.muslimqol.prayer.notify_maghrib",
                "config.muslimqol.prayer.notify_isha"
        );
        for (String key : requiredSalahKeys) {
            assertTrue(en.has(key) && !en.get(key).getAsString().isBlank(), "Missing EN key: " + key);
            assertTrue(ar.has(key) && !ar.get(key).getAsString().isBlank(), "Missing AR key: " + key);
        }
    }

    @Test
    void hudCountdownRendersLocalizedEnglishAndArabicWithoutHardCodedEnglishUnits() throws Exception {
        JsonObject en = loadLangJson("/assets/muslimqol/lang/en_us.json");
        JsonObject ar = loadLangJson("/assets/muslimqol/lang/ar_sa.json");

        ZonedDateTime prayerTime = ZonedDateTime.of(2026, 3, 20, 16, 37, 0, 0, ZoneId.of("Europe/London"));

        SalahHudState state5h20m = stateWithRemaining(prayerTime, Duration.ofHours(5).plusMinutes(20));
        SalahHudState state1h = stateWithRemaining(prayerTime, Duration.ofMinutes(60));
        SalahHudState state59m = stateWithRemaining(prayerTime, Duration.ofMinutes(59));
        SalahHudState stateSubMinute = stateWithRemaining(prayerTime, Duration.ofSeconds(45));

        // 1. English HUD countdown rendering
        assertEquals("in 5h 20m", renderComponent(SalahHudOverlay.buildCountdownLine(state5h20m), en));
        assertEquals("in 1h", renderComponent(SalahHudOverlay.buildCountdownLine(state1h), en));
        assertEquals("in 59m", renderComponent(SalahHudOverlay.buildCountdownLine(state59m), en));
        assertEquals("in <1m", renderComponent(SalahHudOverlay.buildCountdownLine(stateSubMinute), en));

        // 2. Arabic HUD countdown rendering uses pure Arabic unit wording and contains zero English h/m letters
        String ar5h20m = renderComponent(SalahHudOverlay.buildCountdownLine(state5h20m), ar);
        String ar1h = renderComponent(SalahHudOverlay.buildCountdownLine(state1h), ar);
        String ar59m = renderComponent(SalahHudOverlay.buildCountdownLine(state59m), ar);
        String arSubMinute = renderComponent(SalahHudOverlay.buildCountdownLine(stateSubMinute), ar);

        assertEquals("خلال 5 ساعة و20 دقيقة", ar5h20m);
        assertEquals("خلال 1 ساعة", ar1h);
        assertEquals("خلال 59 دقيقة", ar59m);
        assertEquals("خلال أقل من دقيقة", arSubMinute);

        for (String renderedArabic : List.of(ar5h20m, ar1h, ar59m, arSubMinute)) {
            assertFalse(
                    renderedArabic.matches(".*[A-Za-z].*"),
                    "Arabic HUD countdown must not contain English unit letters: " + renderedArabic
            );
        }
    }

    private static SalahHudState stateWithRemaining(ZonedDateTime prayerTime, Duration remaining) {
        return new SalahHudState(
                true,
                Prayer.ASR,
                prayerTime,
                io.github.muslimqol.salah.CountdownFormatter.formatLocalTime(prayerTime),
                remaining,
                io.github.muslimqol.salah.CountdownFormatter.decompose(remaining)
        );
    }

    private JsonObject loadLangJson(String resourcePath) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(stream, resourcePath + " must exist");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String renderComponent(net.minecraft.network.chat.Component component, JsonObject lang) {
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            String pattern = lang.get(tc.getKey()).getAsString();
            Object[] rawArgs = tc.getArgs();
            Object[] resolvedArgs = new Object[rawArgs.length];
            for (int i = 0; i < rawArgs.length; i++) {
                if (rawArgs[i] instanceof net.minecraft.network.chat.Component child) {
                    resolvedArgs[i] = renderComponent(child, lang);
                } else {
                    resolvedArgs[i] = rawArgs[i];
                }
            }
            return String.format(java.util.Locale.ROOT, pattern, resolvedArgs);
        }
        return component.getString();
    }
}
