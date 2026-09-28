package io.github.muslimqol;

import io.github.muslimqol.prayer.AsrMethod;
import io.github.muslimqol.prayer.CalculationMethod;
import io.github.muslimqol.prayer.HighLatitudeRule;
import io.github.muslimqol.prayer.Prayer;
import io.github.muslimqol.prayer.PrayerAdjustments;
import io.github.muslimqol.prayer.PrayerCalculationParameters;
import io.github.muslimqol.prayer.PrayerTimeSource;
import io.github.muslimqol.prayer.PrayerTimes;
import io.github.muslimqol.prayer.PrayerTimesCalculator;
import io.github.muslimqol.qibla.GeoCoordinate;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class PrayerTimesCalculatorTest {

    private static final GeoCoordinate LONDON = GeoCoordinate.of(51.5074, -0.1278);
    private static final GeoCoordinate NEW_YORK = GeoCoordinate.of(40.7128, -74.0060);
    private static final GeoCoordinate JAKARTA = GeoCoordinate.of(-6.2088, 106.8456);
    private static final GeoCoordinate TOKYO = GeoCoordinate.of(35.6762, 139.6503);
    private static final GeoCoordinate SINGAPORE = GeoCoordinate.of(1.3521, 103.8198);
    private static final GeoCoordinate TROMSO = GeoCoordinate.of(69.6492, 18.9553);

    @Test
    public void testDstTransitionLondonAndNewYork() {
        ZoneId londonZone = ZoneId.of("Europe/London");
        PrayerCalculationParameters mwl = PrayerCalculationParameters.of(CalculationMethod.MUSLIM_WORLD_LEAGUE);

        // UK DST starts 2026-03-29: 2026-03-28 is GMT (+00:00), 2026-03-30 is BST (+01:00)
        PrayerTimes londonPreDst = PrayerTimesCalculator.calculate(LONDON, LocalDate.of(2026, 3, 28), londonZone, mwl);
        PrayerTimes londonPostDst = PrayerTimesCalculator.calculate(LONDON, LocalDate.of(2026, 3, 30), londonZone, mwl);

        ZonedDateTime dhuhrPre = londonPreDst.zonedDateTime(Prayer.DHUHR).orElseThrow();
        ZonedDateTime dhuhrPost = londonPostDst.zonedDateTime(Prayer.DHUHR).orElseThrow();

        assertEquals(ZoneOffset.UTC, dhuhrPre.getOffset());
        assertEquals(ZoneOffset.ofHours(1), dhuhrPost.getOffset());
        assertEquals(12, dhuhrPre.getHour());
        assertEquals(13, dhuhrPost.getHour());

        // US DST starts 2026-03-08: 2026-03-07 is EST (-05:00), 2026-03-09 is EDT (-04:00)
        ZoneId nyZone = ZoneId.of("America/New_York");
        PrayerCalculationParameters isna = PrayerCalculationParameters.of(CalculationMethod.NORTH_AMERICA);

        PrayerTimes nyPreDst = PrayerTimesCalculator.calculate(NEW_YORK, LocalDate.of(2026, 3, 7), nyZone, isna);
        PrayerTimes nyPostDst = PrayerTimesCalculator.calculate(NEW_YORK, LocalDate.of(2026, 3, 9), nyZone, isna);

        ZonedDateTime nyDhuhrPre = nyPreDst.zonedDateTime(Prayer.DHUHR).orElseThrow();
        ZonedDateTime nyDhuhrPost = nyPostDst.zonedDateTime(Prayer.DHUHR).orElseThrow();

        assertEquals(ZoneOffset.ofHours(-5), nyDhuhrPre.getOffset());
        assertEquals(ZoneOffset.ofHours(-4), nyDhuhrPost.getOffset());
        assertEquals(12, nyDhuhrPre.getHour());
        assertEquals(13, nyDhuhrPost.getHour());
    }

    @Test
    public void testDateRolloverWhenUtcDateDiffersFromLocalCivilDate() {
        // In Tokyo (UTC+9), Fajr and Sunrise on 2026-03-20 JST occur on 2026-03-19 in UTC.
        ZoneId tokyoZone = ZoneId.of("Asia/Tokyo");
        LocalDate targetCivilDate = LocalDate.of(2026, 3, 20);
        PrayerTimes tokyoTimes = PrayerTimesCalculator.calculate(
                TOKYO,
                targetCivilDate,
                tokyoZone,
                PrayerCalculationParameters.of(CalculationMethod.MUSLIM_WORLD_LEAGUE)
        );

        Instant fajrUtc = tokyoTimes.fajr().instant().orElseThrow();
        assertEquals(LocalDate.of(2026, 3, 19), fajrUtc.atZone(ZoneOffset.UTC).toLocalDate(),
                "Tokyo morning Fajr must occur on the preceding UTC calendar day");
        assertEquals(targetCivilDate, tokyoTimes.zonedDateTime(Prayer.FAJR).orElseThrow().toLocalDate(),
                "Tokyo Fajr in Asia/Tokyo must match the requested local civil date");

        for (Prayer p : Prayer.values()) {
            assertEquals(targetCivilDate, tokyoTimes.zonedDateTime(p).orElseThrow().toLocalDate(),
                    "Tokyo " + p + " must fall on civil date " + targetCivilDate);
        }

        // In New York (UTC-4), Isha on 2026-03-20 EDT occurs after 00:00 UTC on 2026-03-21.
        ZoneId nyZone = ZoneId.of("America/New_York");
        PrayerTimes nyTimes = PrayerTimesCalculator.calculate(
                NEW_YORK,
                targetCivilDate,
                nyZone,
                PrayerCalculationParameters.of(CalculationMethod.MUSLIM_WORLD_LEAGUE)
        );

        Instant nyIshaUtc = nyTimes.isha().instant().orElseThrow();
        assertEquals(LocalDate.of(2026, 3, 21), nyIshaUtc.atZone(ZoneOffset.UTC).toLocalDate(),
                "New York evening Isha (MWL 17°) on March 20 occurs after 00:00 UTC on March 21");
        assertEquals(targetCivilDate, nyTimes.zonedDateTime(Prayer.ISHA).orElseThrow().toLocalDate(),
                "New York Isha in America/New_York must fall on local civil date " + targetCivilDate);
    }

    @Test
    public void testHighLatitudeTwilightAdjustmentRulesInTromso() {
        ZoneId osloZone = ZoneId.of("Europe/Oslo");
        // On 2026-04-25 in Tromsø (69.65° N), sunrise and sunset occur, but the sun never dips to -18° or -17°
        LocalDate persistentTwilightDate = LocalDate.of(2026, 4, 25);

        // 1. HighLatitudeRule.NONE -> Fajr and Isha remain explicitly UNAVAILABLE
        PrayerTimes noneSchedule = PrayerTimesCalculator.calculate(
                TROMSO,
                persistentTwilightDate,
                osloZone,
                PrayerCalculationParameters.of(CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD, HighLatitudeRule.NONE)
        );

        assertFalse(noneSchedule.fajr().isAvailable());
        assertEquals(PrayerTimeSource.UNAVAILABLE, noneSchedule.fajr().source());
        assertFalse(noneSchedule.isha().isAvailable());
        assertEquals(PrayerTimeSource.UNAVAILABLE, noneSchedule.isha().source());

        assertTrue(noneSchedule.sunrise().isAvailable());
        assertTrue(noneSchedule.dhuhr().isAvailable());
        assertTrue(noneSchedule.asr().isAvailable());
        assertTrue(noneSchedule.maghrib().isAvailable());

        // 2. MIDDLE_OF_NIGHT, SEVENTH_OF_NIGHT, TWILIGHT_ANGLE -> deterministic HIGH_LATITUDE_ADJUSTED
        for (HighLatitudeRule rule : new HighLatitudeRule[]{
                HighLatitudeRule.MIDDLE_OF_NIGHT,
                HighLatitudeRule.SEVENTH_OF_NIGHT,
                HighLatitudeRule.TWILIGHT_ANGLE
        }) {
            PrayerTimes adjusted = PrayerTimesCalculator.calculate(
                    TROMSO,
                    persistentTwilightDate,
                    osloZone,
                    PrayerCalculationParameters.of(CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD, rule)
            );

            assertTrue(adjusted.allDefined(), "All events must be defined under " + rule);
            assertEquals(PrayerTimeSource.HIGH_LATITUDE_ADJUSTED, adjusted.fajr().source());
            assertEquals(PrayerTimeSource.HIGH_LATITUDE_ADJUSTED, adjusted.isha().source());
            assertEquals(PrayerTimeSource.ASTRONOMICAL, adjusted.sunrise().source());
            assertEquals(PrayerTimeSource.ASTRONOMICAL, adjusted.maghrib().source());

            assertTrue(adjusted.fajr().instant().orElseThrow().isBefore(adjusted.sunrise().instant().orElseThrow()));
            assertTrue(adjusted.isha().instant().orElseThrow().isAfter(adjusted.maghrib().instant().orElseThrow()));
        }
    }

    @Test
    public void testPolarDayAndPolarNightDoNotFabricateSchedules() {
        ZoneId osloZone = ZoneId.of("Europe/Oslo");

        // Summer solstice (Midnight Sun) in Tromsø: sunrise and sunset do not occur
        PrayerTimes polarDay = PrayerTimesCalculator.calculate(
                TROMSO,
                LocalDate.of(2026, 6, 21),
                osloZone,
                PrayerCalculationParameters.of(CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD, HighLatitudeRule.MIDDLE_OF_NIGHT)
        );

        assertFalse(polarDay.sunrise().isAvailable());
        assertFalse(polarDay.maghrib().isAvailable());
        assertFalse(polarDay.fajr().isAvailable(), "High-lat rule must not fabricate Fajr when sunrise/sunset do not exist");
        assertFalse(polarDay.isha().isAvailable(), "High-lat rule must not fabricate Isha when sunrise/sunset do not exist");
        assertTrue(polarDay.dhuhr().isAvailable());
        assertTrue(polarDay.asr().isAvailable());

        // Winter solstice (Polar Night) in Tromsø: sun remains below horizon all day
        PrayerTimes polarNight = PrayerTimesCalculator.calculate(
                TROMSO,
                LocalDate.of(2026, 12, 21),
                osloZone,
                PrayerCalculationParameters.of(CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD, HighLatitudeRule.MIDDLE_OF_NIGHT)
        );

        assertFalse(polarNight.sunrise().isAvailable());
        assertFalse(polarNight.maghrib().isAvailable());
        assertFalse(polarNight.asr().isAvailable(), "Asr is unavailable when sun does not rise above horizon at noon");
        assertFalse(polarNight.fajr().isAvailable());
        assertFalse(polarNight.isha().isAvailable());
        assertTrue(polarNight.dhuhr().isAvailable(), "Solar meridian transit (Dhuhr) remains astronomically defined");
    }

    @Test
    public void testHanafiAsrIsAlwaysLaterThanStandardAsr() {
        LocalDate date = LocalDate.of(2026, 3, 20);
        GeoCoordinate[] locations = {LONDON, NEW_YORK, JAKARTA, TOKYO, SINGAPORE};

        for (GeoCoordinate loc : locations) {
            PrayerTimes standard = PrayerTimesCalculator.calculate(
                    loc,
                    date,
                    ZoneOffset.UTC,
                    PrayerCalculationParameters.of(CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD, HighLatitudeRule.MIDDLE_OF_NIGHT)
            );
            PrayerTimes hanafi = PrayerTimesCalculator.calculate(
                    loc,
                    date,
                    ZoneOffset.UTC,
                    PrayerCalculationParameters.of(CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.HANAFI, HighLatitudeRule.MIDDLE_OF_NIGHT)
            );

            Instant stdAsr = standard.asr().instant().orElseThrow();
            Instant hanAsr = hanafi.asr().instant().orElseThrow();
            assertTrue(hanAsr.isAfter(stdAsr), "Hanafi Asr must be strictly later than Standard Asr at " + loc);
            long diffMinutes = Duration.between(stdAsr, hanAsr).toMinutes();
            assertTrue(diffMinutes >= 30 && diffMinutes <= 90, "Expected realistic Asr difference (30..90 min), got " + diffMinutes);
        }
    }

    @Test
    public void testCustomAnglesAndMinuteAdjustmentsAndPrivacy() {
        LocalDate date = LocalDate.of(2026, 3, 20);
        ZoneId zone = ZoneId.of("Europe/London");
        PrayerAdjustments adjustments = new PrayerAdjustments(5, -2, 3, 0, 4, -5);

        PrayerCalculationParameters baseParams = PrayerCalculationParameters.custom(
                18.5,
                16.5,
                AsrMethod.STANDARD,
                HighLatitudeRule.MIDDLE_OF_NIGHT,
                PrayerAdjustments.NONE
        );
        PrayerCalculationParameters adjustedParams = baseParams.withAdjustments(adjustments);

        PrayerTimes baseTimes = PrayerTimesCalculator.calculate(LONDON, date, zone, baseParams);
        PrayerTimes adjustedTimes = PrayerTimesCalculator.calculate(LONDON, date, zone, adjustedParams);

        assertEquals(Duration.ofMinutes(5), Duration.between(baseTimes.fajr().instant().orElseThrow(), adjustedTimes.fajr().instant().orElseThrow()));
        assertEquals(Duration.ofMinutes(-2), Duration.between(baseTimes.sunrise().instant().orElseThrow(), adjustedTimes.sunrise().instant().orElseThrow()));
        assertEquals(Duration.ofMinutes(3), Duration.between(baseTimes.dhuhr().instant().orElseThrow(), adjustedTimes.dhuhr().instant().orElseThrow()));
        assertEquals(Duration.ZERO, Duration.between(baseTimes.asr().instant().orElseThrow(), adjustedTimes.asr().instant().orElseThrow()));
        assertEquals(Duration.ofMinutes(4), Duration.between(baseTimes.maghrib().instant().orElseThrow(), adjustedTimes.maghrib().instant().orElseThrow()));
        assertEquals(Duration.ofMinutes(-5), Duration.between(baseTimes.isha().instant().orElseThrow(), adjustedTimes.isha().instant().orElseThrow()));

        // Privacy check: toString() must not contain raw observer coordinates
        String dump = adjustedTimes.toString();
        assertFalse(dump.contains("51.5074"), "PrayerTimes.toString() must not leak observer latitude");
        assertFalse(dump.contains("-0.1278"), "PrayerTimes.toString() must not leak observer longitude");

        // Obligatory prayer flag check
        assertTrue(Prayer.FAJR.isObligatoryPrayer());
        assertFalse(Prayer.SUNRISE.isObligatoryPrayer());
        assertTrue(Prayer.DHUHR.isObligatoryPrayer());
        assertTrue(Prayer.ASR.isObligatoryPrayer());
        assertTrue(Prayer.MAGHRIB.isObligatoryPrayer());
        assertTrue(Prayer.ISHA.isObligatoryPrayer());
    }
}
