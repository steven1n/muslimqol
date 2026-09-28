package io.github.muslimqol;

import io.github.muslimqol.salah.CountdownFormatter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CountdownFormatterTest {

    @Test
    void formatsRequiredBoundaryDurationsWithoutSeconds() {
        assertEquals("5h 20m", CountdownFormatter.formatCountdown(Duration.ofHours(5).plusMinutes(20)));
        assertEquals("2h 05m", CountdownFormatter.formatCountdown(Duration.ofHours(2).plusMinutes(5)));
        assertEquals("1h 00m", CountdownFormatter.formatCountdown(Duration.ofMinutes(60)));
        assertEquals("59m", CountdownFormatter.formatCountdown(Duration.ofMinutes(59)));
        assertEquals("59m", CountdownFormatter.formatCountdown(Duration.ofMinutes(59).plusSeconds(59)));
        assertEquals("1m", CountdownFormatter.formatCountdown(Duration.ofSeconds(60)));
        assertEquals("<1m", CountdownFormatter.formatCountdown(Duration.ofSeconds(59)));
        assertEquals("<1m", CountdownFormatter.formatCountdown(Duration.ofSeconds(1)));
        assertEquals("<1m", CountdownFormatter.formatCountdown(Duration.ZERO));
        assertEquals("24h 00m", CountdownFormatter.formatCountdown(Duration.ofHours(24)));
    }

    @Test
    void neverProducesNegativeText() {
        assertEquals("<1m", CountdownFormatter.formatCountdown(Duration.ofSeconds(-1)));
        assertEquals("<1m", CountdownFormatter.formatCountdown(Duration.ofMinutes(-30)));
    }

    @Test
    void formatsLocalTimeInConfiguredZoneAsHhMm() {
        Instant instant = Instant.parse("2026-03-20T16:37:45Z");
        ZonedDateTime londonTime = instant.atZone(ZoneId.of("Europe/London"));
        ZonedDateTime tokyoTime = instant.atZone(ZoneId.of("Asia/Tokyo"));

        assertEquals("16:37", CountdownFormatter.formatLocalTime(londonTime));
        assertEquals("01:37", CountdownFormatter.formatLocalTime(tokyoTime));
    }
}
