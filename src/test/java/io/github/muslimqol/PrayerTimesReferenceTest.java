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
 * Validates {@link PrayerTimesCalculator} against the frozen independent reference fixture
 * ({@code /reference/prayer-times-reference.json}) generated from the Jean Meeus (Ch. 15 & Ch. 25)
 * / Adhan 3-point Right-Ascension and Sidereal-Time interpolation model.
 */
public class PrayerTimesReferenceTest {

    @Test
    public void testAllReferenceCasesWithinTolerance() throws Exception {
        JsonObject root;
        try (var stream = getClass().getResourceAsStream("/reference/prayer-times-reference.json")) {
            assertNotNull(stream, "prayer-times-reference.json must exist in test resources");
            root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        long maxAllowedSeconds = root.get("tolerance_seconds").getAsLong();
        assertEquals(90L, maxAllowedSeconds);

        JsonArray cases = root.getAsJsonArray("cases");
        assertTrue(cases.size() >= 19, "Expected at least 19 reference cases");

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

            assertTrue(actual.allDefined(), "All events must be defined for ordinary reference case: " + id);

            JsonObject expectedEvents = testCase.getAsJsonObject("events");
            Instant previousInstant = null;

            for (Prayer prayer : Prayer.values()) {
                PrayerMoment moment = actual.moment(prayer);
                assertTrue(moment.isAvailable(), id + " missing " + prayer);
                assertEquals(PrayerTimeSource.ASTRONOMICAL, moment.source(), id + " " + prayer + " must be ASTRONOMICAL");

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
                assertEquals(civilDate, actualLocal.toLocalDate(),
                        id + " " + prayer + " must fall on requested local civil date " + civilDate);
            }
        }

        assertTrue(maxObservedDeviationSeconds <= maxAllowedSeconds,
                "Max observed deviation (" + maxObservedDeviationSeconds + "s) exceeded tolerance");
    }
}
