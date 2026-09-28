package io.github.muslimqol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.muslimqol.prayer.AsrMethod;
import io.github.muslimqol.prayer.CalculationMethod;
import io.github.muslimqol.prayer.HighLatitudeRule;
import io.github.muslimqol.prayer.Prayer;
import io.github.muslimqol.prayer.PrayerCalculationParameters;
import io.github.muslimqol.prayer.PrayerMoment;
import io.github.muslimqol.prayer.PrayerTimeSource;
import io.github.muslimqol.prayer.PrayerTimes;
import io.github.muslimqol.prayer.PrayerTimesCalculator;
import io.github.muslimqol.qibla.GeoCoordinate;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates {@link PrayerTimesCalculator} against:
 * <ol>
 *   <li>Direct outputs from pinned Batoul Apps Adhan ({@code https://github.com/batoulapps/adhan-java},
 *       tag {@code v1.2.1}, commit {@code eefc4ed1b910ec144dff247f45b2b23a16c7d0c2}) in
 *       {@code /reference/prayer-times-reference.json}.</li>
 *   <li>Secondary unrounded Jean Meeus (2nd Ed., Ch. 15 & Ch. 25) cross-validation fixture in
 *       {@code /reference/prayer-times-meeus-reference.json}.</li>
 * </ol>
 */
public class PrayerTimesReferenceTest {

    @Test
    public void testAllDirectAdhanReferenceCasesWithinTolerance() throws Exception {
        JsonObject root;
        try (var stream = getClass().getResourceAsStream("/reference/prayer-times-reference.json")) {
            assertNotNull(stream, "prayer-times-reference.json must exist in test resources");
            root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        assertEquals("https://github.com/batoulapps/adhan-java", root.get("reference_repository").getAsString());
        assertEquals("v1.2.1", root.get("reference_version").getAsString());
        assertEquals("eefc4ed1b910ec144dff247f45b2b23a16c7d0c2", root.get("reference_commit_sha").getAsString());

        long maxAllowedSeconds = root.get("tolerance_seconds").getAsLong();
        assertEquals(90L, maxAllowedSeconds);

        JsonArray cases = root.getAsJsonArray("cases");
        assertEquals(26, cases.size(), "Expected 26 direct Adhan v1.2.1 reference cases");

        long maxObservedDeviationSeconds = 0L;
        int highLatSafeBoundaryOverrideCount = 0;

        for (JsonElement element : cases) {
            JsonObject testCase = element.getAsJsonObject();
            String id = testCase.get("id").getAsString();
            double lat = testCase.get("latitude").getAsDouble();
            double lon = testCase.get("longitude").getAsDouble();
            ZoneId zoneId = ZoneId.of(testCase.get("zone_id").getAsString());
            LocalDate civilDate = LocalDate.parse(testCase.get("civil_date").getAsString());
            CalculationMethod method = CalculationMethod.valueOf(testCase.get("calculation_method").getAsString());
            AsrMethod asrMethod = AsrMethod.valueOf(testCase.get("asr_method").getAsString());
            HighLatitudeRule highLatRule = HighLatitudeRule.valueOf(testCase.get("high_latitude_rule").getAsString());
            boolean astroTwilightExists = testCase.get("astronomical_twilight_exists").getAsBoolean();
            PrayerTimeSource expectedFajrSource = PrayerTimeSource.valueOf(testCase.get("expected_fajr_source").getAsString());
            PrayerTimeSource expectedIshaSource = PrayerTimeSource.valueOf(testCase.get("expected_isha_source").getAsString());

            GeoCoordinate observer = GeoCoordinate.of(lat, lon);
            PrayerCalculationParameters params = PrayerCalculationParameters.of(method, asrMethod, highLatRule);
            PrayerTimes actual = PrayerTimesCalculator.calculate(observer, civilDate, zoneId, params);

            assertTrue(actual.allDefined(), "All events must be defined for reference case: " + id);
            assertEquals(expectedFajrSource, actual.fajr().source(), id + " Fajr source mismatch");
            assertEquals(expectedIshaSource, actual.isha().source(), id + " Isha source mismatch");

            if (astroTwilightExists && expectedFajrSource == PrayerTimeSource.HIGH_LATITUDE_ADJUSTED) {
                highLatSafeBoundaryOverrideCount++;
                PrayerTimes unadjusted = PrayerTimesCalculator.calculate(
                        observer,
                        civilDate,
                        zoneId,
                        PrayerCalculationParameters.of(method, asrMethod, HighLatitudeRule.NONE)
                );
                assertTrue(unadjusted.fajr().isAvailable(), id + " astronomical Fajr must exist under NONE");
                assertTrue(unadjusted.isha().isAvailable(), id + " astronomical Isha must exist under NONE");
                assertEquals(PrayerTimeSource.ASTRONOMICAL, unadjusted.fajr().source());
                assertEquals(PrayerTimeSource.ASTRONOMICAL, unadjusted.isha().source());
                assertTrue(unadjusted.fajr().instant().orElseThrow().isBefore(actual.fajr().instant().orElseThrow()),
                        id + " astronomical Fajr must be earlier than safe high-latitude Fajr boundary");
                assertTrue(unadjusted.isha().instant().orElseThrow().isAfter(actual.isha().instant().orElseThrow()),
                        id + " astronomical Isha must be later than safe high-latitude Isha boundary");
            }

            JsonObject expectedEvents = testCase.getAsJsonObject("events");
            Instant previousInstant = null;

            for (Prayer prayer : Prayer.values()) {
                PrayerMoment moment = actual.moment(prayer);
                assertTrue(moment.isAvailable(), id + " missing " + prayer);
                if (prayer != Prayer.FAJR && prayer != Prayer.ISHA) {
                    assertEquals(PrayerTimeSource.ASTRONOMICAL, moment.source(), id + " " + prayer + " must be ASTRONOMICAL");
                }

                Instant actualInstant = moment.instant().orElseThrow();
                if (previousInstant != null) {
                    assertTrue(actualInstant.isAfter(previousInstant),
                            id + " chronological order violated at " + prayer + ": " + actualInstant + " <= " + previousInstant);
                }
                previousInstant = actualInstant;

                JsonObject expectedEvent = expectedEvents.getAsJsonObject(prayer.name());
                Instant expectedInstant = Instant.parse(expectedEvent.get("instant").getAsString());
                ZonedDateTime expectedLocal = ZonedDateTime.parse(
                        expectedEvent.get("local_time").getAsString(),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
                );

                long deviationSeconds = Math.abs(Duration.between(expectedInstant, actualInstant).getSeconds());
                if (deviationSeconds > maxObservedDeviationSeconds) {
                    maxObservedDeviationSeconds = deviationSeconds;
                }

                assertTrue(deviationSeconds <= maxAllowedSeconds,
                        String.format("Case %s [%s]: expected %s, got %s (deviation %ds > %ds)",
                                id, prayer, expectedInstant, actualInstant, deviationSeconds, maxAllowedSeconds));

                ZonedDateTime actualLocal = actual.zonedDateTime(prayer).orElseThrow();
                assertEquals(expectedLocal.getOffset(), actualLocal.getOffset(),
                        id + " " + prayer + " timezone offset mismatch");
            }
        }

        assertTrue(highLatSafeBoundaryOverrideCount >= 4,
                "Expected at least 4 high-latitude cases where existing astronomical twilight is clamped by safe boundary");
        assertTrue(maxObservedDeviationSeconds <= maxAllowedSeconds,
                "Max observed deviation (" + maxObservedDeviationSeconds + "s) exceeded tolerance");
    }

    @Test
    public void testSecondaryMeeusReferenceCasesWithinTolerance() throws Exception {
        JsonObject root;
        try (var stream = getClass().getResourceAsStream("/reference/prayer-times-meeus-reference.json")) {
            assertNotNull(stream, "prayer-times-meeus-reference.json must exist in test resources");
            root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        long maxAllowedSeconds = root.get("tolerance_seconds").getAsLong();
        JsonArray cases = root.getAsJsonArray("cases");
        assertEquals(19, cases.size());

        long maxObservedDeviationSeconds = 0L;

        for (JsonElement element : cases) {
            JsonObject testCase = element.getAsJsonObject();
            String id = testCase.get("id").getAsString();
            double lat = testCase.get("latitude").getAsDouble();
            double lon = testCase.get("longitude").getAsDouble();
            ZoneId zoneId = ZoneId.of(testCase.get("zone_id").getAsString());
            LocalDate civilDate = LocalDate.parse(testCase.get("civil_date").getAsString());
            CalculationMethod method = CalculationMethod.valueOf(testCase.get("calculation_method").getAsString());
            AsrMethod asrMethod = AsrMethod.valueOf(testCase.get("asr_method").getAsString());
            HighLatitudeRule highLatRule = HighLatitudeRule.valueOf(testCase.get("high_latitude_rule").getAsString());

            GeoCoordinate observer = GeoCoordinate.of(lat, lon);
            PrayerCalculationParameters params = PrayerCalculationParameters.of(method, asrMethod, highLatRule);
            PrayerTimes actual = PrayerTimesCalculator.calculate(observer, civilDate, zoneId, params);

            JsonObject expectedEvents = testCase.getAsJsonObject("events");
            for (Prayer prayer : Prayer.values()) {
                Instant actualInstant = actual.moment(prayer).instant().orElseThrow();
                Instant expectedInstant = Instant.parse(
                        expectedEvents.getAsJsonObject(prayer.name()).get("instant").getAsString()
                );
                long deviationSeconds = Math.abs(Duration.between(expectedInstant, actualInstant).getSeconds());
                if (deviationSeconds > maxObservedDeviationSeconds) {
                    maxObservedDeviationSeconds = deviationSeconds;
                }
                assertTrue(deviationSeconds <= maxAllowedSeconds,
                        id + " [" + prayer + "] deviation " + deviationSeconds + "s exceeded " + maxAllowedSeconds + "s");
            }
        }

        assertTrue(maxObservedDeviationSeconds <= 20L,
                "Expected max deviation against unrounded Meeus reference <= 20s, got " + maxObservedDeviationSeconds + "s");
    }
}
