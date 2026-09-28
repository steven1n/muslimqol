package io.github.muslimqol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.muslimqol.client.prayer.PrayerTimesClientService;
import io.github.muslimqol.config.ClientConfig;
import io.github.muslimqol.config.CommonConfig;
import io.github.muslimqol.prayer.AsrMethod;
import io.github.muslimqol.prayer.CalculationMethod;
import io.github.muslimqol.prayer.HighLatitudeRule;
import io.github.muslimqol.prayer.Prayer;
import io.github.muslimqol.prayer.PrayerAdjustments;
import io.github.muslimqol.prayer.PrayerCalculationParameters;
import io.github.muslimqol.prayer.PrayerTimes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PrayerConfigAndServiceTest {

    @AfterEach
    public void tearDown() {
        PrayerTimesClientService.resetCache();
    }

    @Test
    public void testValidAndInvalidCalculationMethods() {
        for (CalculationMethod method : CalculationMethod.values()) {
            assertEquals(Optional.of(method), CalculationMethod.fromConfigName(method.name()));
            assertEquals(Optional.of(method), CalculationMethod.fromConfigName("  " + method.name().toLowerCase() + "  "));
        }

        // Deferred or malformed methods must return empty without throwing
        assertTrue(CalculationMethod.fromConfigName("UMM_AL_QURA").isEmpty());
        assertTrue(CalculationMethod.fromConfigName("QATAR").isEmpty());
        assertTrue(CalculationMethod.fromConfigName("MOONSIGHTING_COMMITTEE").isEmpty());
        assertTrue(CalculationMethod.fromConfigName("UNKNOWN_METHOD").isEmpty());
        assertTrue(CalculationMethod.fromConfigName("").isEmpty());
        assertTrue(CalculationMethod.fromConfigName(null).isEmpty());
    }

    @Test
    public void testValidAndInvalidAsrAndHighLatitudeRules() {
        assertEquals(Optional.of(AsrMethod.STANDARD), AsrMethod.fromConfigName("STANDARD"));
        assertEquals(Optional.of(AsrMethod.HANAFI), AsrMethod.fromConfigName("hanafi"));
        assertTrue(AsrMethod.fromConfigName("INVALID").isEmpty());
        assertTrue(AsrMethod.fromConfigName("").isEmpty());

        for (HighLatitudeRule rule : HighLatitudeRule.values()) {
            assertEquals(Optional.of(rule), HighLatitudeRule.fromConfigName(rule.name()));
        }
        assertTrue(HighLatitudeRule.fromConfigName("INVALID_RULE").isEmpty());
        assertTrue(HighLatitudeRule.fromConfigName(null).isEmpty());
    }

    @Test
    public void testZoneIdResolution() {
        ZoneId fallback = ZoneId.of("UTC");

        // Blank -> fallback
        assertEquals(Optional.of(fallback), PrayerTimesClientService.resolveZoneId("", fallback));
        assertEquals(Optional.of(fallback), PrayerTimesClientService.resolveZoneId("   ", fallback));
        assertEquals(Optional.of(fallback), PrayerTimesClientService.resolveZoneId(null, fallback));

        // Explicit valid IANA zones
        assertEquals(Optional.of(ZoneId.of("Europe/London")), PrayerTimesClientService.resolveZoneId("Europe/London", fallback));
        assertEquals(Optional.of(ZoneId.of("America/New_York")), PrayerTimesClientService.resolveZoneId(" America/New_York ", fallback));
        assertEquals(Optional.of(ZoneId.of("Asia/Tokyo")), PrayerTimesClientService.resolveZoneId("Asia/Tokyo", fallback));
        assertEquals(Optional.of(ZoneId.of("Asia/Singapore")), PrayerTimesClientService.resolveZoneId("Asia/Singapore", fallback));

        // Invalid zone -> empty (never crashes)
        assertTrue(PrayerTimesClientService.resolveZoneId("Invalid/Timezone_ID", fallback).isEmpty());
        assertTrue(PrayerTimesClientService.resolveZoneId("Mars/Olympus_Mons", fallback).isEmpty());
    }

    @Test
    public void testCustomAngleAndAdjustmentBounds() {
        // Valid custom angles and adjustments
        Optional<PrayerCalculationParameters> valid = PrayerTimesClientService.resolveParameters(
                "CUSTOM", 18.5, 16.0, "HANAFI", "SEVENTH_OF_NIGHT",
                -10, 0, 5, 15, -5, 20
        );
        assertTrue(valid.isPresent());
        assertEquals(CalculationMethod.CUSTOM, valid.get().method());
        assertEquals(18.5, valid.get().fajrAngleDeg(), 1e-6);
        assertEquals(16.0, valid.get().ishaAngleDeg(), 1e-6);
        assertEquals(AsrMethod.HANAFI, valid.get().asrMethod());
        assertEquals(HighLatitudeRule.SEVENTH_OF_NIGHT, valid.get().highLatitudeRule());

        // Out-of-bounds custom angles (< 1.0 or > 30.0) -> empty
        assertTrue(PrayerTimesClientService.resolveParameters(
                "CUSTOM", 0.5, 17.0, "STANDARD", "MIDDLE_OF_NIGHT", 0, 0, 0, 0, 0, 0
        ).isEmpty());
        assertTrue(PrayerTimesClientService.resolveParameters(
                "CUSTOM", 18.0, 35.0, "STANDARD", "MIDDLE_OF_NIGHT", 0, 0, 0, 0, 0, 0
        ).isEmpty());
        assertTrue(PrayerTimesClientService.resolveParameters(
                "CUSTOM", Double.NaN, 17.0, "STANDARD", "MIDDLE_OF_NIGHT", 0, 0, 0, 0, 0, 0
        ).isEmpty());

        // Out-of-bounds adjustments (< -60 or > 60) -> empty
        assertTrue(PrayerTimesClientService.resolveParameters(
                "MUSLIM_WORLD_LEAGUE", 18.0, 17.0, "STANDARD", "MIDDLE_OF_NIGHT", -61, 0, 0, 0, 0, 0
        ).isEmpty());
        assertTrue(PrayerTimesClientService.resolveParameters(
                "MUSLIM_WORLD_LEAGUE", 18.0, 17.0, "STANDARD", "MIDDLE_OF_NIGHT", 0, 0, 0, 0, 0, 61
        ).isEmpty());

        // Direct record validation
        assertThrows(IllegalArgumentException.class, () -> new PrayerAdjustments(-61, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PrayerAdjustments(0, 0, 61, 0, 0, 0));
    }

    @Test
    public void testClientServiceCachingAndInvalidation() {
        Clock day1Clock = Clock.fixed(Instant.parse("2026-03-20T12:00:00Z"), ZoneOffset.UTC);
        Clock day2Clock = Clock.fixed(Instant.parse("2026-03-21T12:00:00Z"), ZoneOffset.UTC);

        // 1. Initial calculation
        Optional<PrayerTimes> first = PrayerTimesClientService.evaluateAndCache(
                true, true, 51.5074, -0.1278, "Europe/London",
                "MUSLIM_WORLD_LEAGUE", 18.0, 17.0, "STANDARD", "MIDDLE_OF_NIGHT",
                0, 0, 0, 0, 0, 0, day1Clock
        );
        assertTrue(first.isPresent());

        // 2. Identical inputs return exact same cached instance
        Optional<PrayerTimes> cached = PrayerTimesClientService.evaluateAndCache(
                true, true, 51.5074, -0.1278, "Europe/London",
                "MUSLIM_WORLD_LEAGUE", 18.0, 17.0, "STANDARD", "MIDDLE_OF_NIGHT",
                0, 0, 0, 0, 0, 0, day1Clock
        );
        assertTrue(cached.isPresent());
        assertSame(first.get(), cached.get(), "Expected cached PrayerTimes instance on identical inputs");

        // 3. Date rollover invalidates cache
        Optional<PrayerTimes> nextDay = PrayerTimesClientService.evaluateAndCache(
                true, true, 51.5074, -0.1278, "Europe/London",
                "MUSLIM_WORLD_LEAGUE", 18.0, 17.0, "STANDARD", "MIDDLE_OF_NIGHT",
                0, 0, 0, 0, 0, 0, day2Clock
        );
        assertTrue(nextDay.isPresent());
        assertNotSame(first.get(), nextDay.get());
        assertEquals(LocalDate.of(2026, 3, 21), nextDay.get().civilDate());

        // 4. Coordinate change invalidates cache
        Optional<PrayerTimes> newCoord = PrayerTimesClientService.evaluateAndCache(
                true, true, 40.7128, -74.0060, "America/New_York",
                "MUSLIM_WORLD_LEAGUE", 18.0, 17.0, "STANDARD", "MIDDLE_OF_NIGHT",
                0, 0, 0, 0, 0, 0, day2Clock
        );
        assertTrue(newCoord.isPresent());
        assertNotEquals(nextDay.get().dhuhr().instant(), newCoord.get().dhuhr().instant());

        // 5. Method / Asr / Adjustment change invalidates cache
        Optional<PrayerTimes> hanafi = PrayerTimesClientService.evaluateAndCache(
                true, true, 40.7128, -74.0060, "America/New_York",
                "MUSLIM_WORLD_LEAGUE", 18.0, 17.0, "HANAFI", "MIDDLE_OF_NIGHT",
                0, 0, 0, 0, 0, 0, day2Clock
        );
        assertTrue(hanafi.isPresent());
        assertNotEquals(newCoord.get().asr().instant(), hanafi.get().asr().instant());

        // 6. Unconfigured location or disabled prayer returns empty
        assertTrue(PrayerTimesClientService.evaluateAndCache(
                false, true, 51.5074, -0.1278, "Europe/London",
                "MUSLIM_WORLD_LEAGUE", 18.0, 17.0, "STANDARD", "MIDDLE_OF_NIGHT",
                0, 0, 0, 0, 0, 0, day1Clock
        ).isEmpty());

        assertTrue(PrayerTimesClientService.evaluateAndCache(
                true, false, 51.5074, -0.1278, "Europe/London",
                "MUSLIM_WORLD_LEAGUE", 18.0, 17.0, "STANDARD", "MIDDLE_OF_NIGHT",
                0, 0, 0, 0, 0, 0, day1Clock
        ).isEmpty());
    }

    @Test
    public void testClientConfigDefaultsAndLocalizationParity() throws Exception {
        assertTrue(ClientConfig.PRAYER_ENABLED.getDefault());
        assertEquals("", ClientConfig.PRAYER_ZONE_ID.getDefault());
        assertEquals("MUSLIM_WORLD_LEAGUE", ClientConfig.PRAYER_CALCULATION_METHOD.getDefault());
        assertEquals(18.0, ClientConfig.PRAYER_CUSTOM_FAJR_ANGLE.getDefault(), 1e-6);
        assertEquals(17.0, ClientConfig.PRAYER_CUSTOM_ISHA_ANGLE.getDefault(), 1e-6);
        assertEquals("STANDARD", ClientConfig.PRAYER_ASR_METHOD.getDefault());
        assertEquals("MIDDLE_OF_NIGHT", ClientConfig.PRAYER_HIGH_LATITUDE_RULE.getDefault());
        assertEquals(0, ClientConfig.PRAYER_ADJUST_FAJR.getDefault());
        assertEquals(0, ClientConfig.PRAYER_ADJUST_ISHA.getDefault());

        // CommonConfig must not contain prayer or location settings
        String commonSpec = CommonConfig.SPEC.toString().toLowerCase();
        assertFalse(commonSpec.contains("prayer"));
        assertFalse(commonSpec.contains("fajr"));

        // Localization parity check
        JsonObject en;
        JsonObject ar;
        try (var enStream = getClass().getResourceAsStream("/assets/muslimqol/lang/en_us.json");
             var arStream = getClass().getResourceAsStream("/assets/muslimqol/lang/ar_sa.json")) {
            assertNotNull(enStream);
            assertNotNull(arStream);
            en = JsonParser.parseReader(new InputStreamReader(enStream, StandardCharsets.UTF_8)).getAsJsonObject();
            ar = JsonParser.parseReader(new InputStreamReader(arStream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        for (Prayer prayer : Prayer.values()) {
            String key = prayer.translationKey();
            assertTrue(en.has(key), "en_us missing " + key);
            assertTrue(ar.has(key), "ar_sa missing " + key);
            assertFalse(en.get(key).getAsString().isBlank());
            assertFalse(ar.get(key).getAsString().isBlank());
        }

        Set<String> configKeys = Set.of(
                "config.muslimqol.prayer.enabled",
                "config.muslimqol.prayer.zone_id",
                "config.muslimqol.prayer.calculation_method",
                "config.muslimqol.prayer.custom_fajr_angle",
                "config.muslimqol.prayer.custom_isha_angle",
                "config.muslimqol.prayer.asr_method",
                "config.muslimqol.prayer.high_latitude_rule"
        );
        for (String key : configKeys) {
            assertTrue(en.has(key), "en_us missing " + key);
            assertTrue(ar.has(key), "ar_sa missing " + key);
        }
    }
}
