package io.github.muslimqol;

import io.github.muslimqol.salah.CountdownFormatter;
import io.github.muslimqol.salah.CountdownValue;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountdownFormatterTest {

    @Test
    void decomposesRequiredBoundaryDurationsIntoStructuredCountdownValues() {
        assertEquals(new CountdownValue(0, 0, true), CountdownFormatter.decompose(Duration.ofSeconds(59)));
        assertEquals(new CountdownValue(0, 1, false), CountdownFormatter.decompose(Duration.ofSeconds(60)));
        assertEquals(new CountdownValue(0, 59, false), CountdownFormatter.decompose(Duration.ofMinutes(59)));
        assertEquals(new CountdownValue(1, 0, false), CountdownFormatter.decompose(Duration.ofMinutes(60)));
        assertEquals(new CountdownValue(5, 20, false), CountdownFormatter.decompose(Duration.ofHours(5).plusMinutes(20)));
        assertEquals(new CountdownValue(24, 0, false), CountdownFormatter.decompose(Duration.ofHours(24)));
        assertEquals(new CountdownValue(0, 0, true), CountdownFormatter.decompose(Duration.ZERO));
        assertEquals(new CountdownValue(0, 0, true), CountdownFormatter.decompose(Duration.ofSeconds(-30)));
        assertEquals("05", new CountdownValue(2, 5, false).zeroPaddedMinutes());
        assertEquals("20", new CountdownValue(5, 20, false).zeroPaddedMinutes());
    }

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

    @Test
    void pureSalahPackageHasZeroMinecraftOrNeoForgeDependencies() throws Exception {
        Path salahDir = Path.of("src/main/java/io/github/muslimqol/salah");
        assertTrue(Files.isDirectory(salahDir), "Pure salah directory must exist");

        List<Path> javaFiles;
        try (Stream<Path> stream = Files.list(salahDir)) {
            javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertFalse(javaFiles.isEmpty(), "Pure salah package must contain Java files");

        for (Path file : javaFiles) {
            String source = Files.readString(file);
            assertFalse(source.contains("net.minecraft."), "Forbidden Minecraft import in " + file.getFileName());
            assertFalse(source.contains("net.neoforged."), "Forbidden NeoForge import in " + file.getFileName());
            assertFalse(source.contains("com.mojang."), "Forbidden Mojang import in " + file.getFileName());
        }
    }
}
