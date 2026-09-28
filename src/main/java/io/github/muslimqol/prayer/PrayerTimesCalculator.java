package io.github.muslimqol.prayer;

import io.github.muslimqol.prayer.astronomy.SolarMath;
import io.github.muslimqol.qibla.GeoCoordinate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Pure deterministic calculator for daily prayer schedules.
 *
 * <p>Operates completely offline with zero Minecraft, NeoForge, or network dependencies.
 */
public final class PrayerTimesCalculator {

    private PrayerTimesCalculator() {}

    /**
     * Calculates the six daily solar and prayer events for the given observer coordinate,
     * local civil date, time zone, and calculation parameters.
     *
     * @param observer   real-world observer latitude and longitude
     * @param civilDate  target local civil date in {@code zoneId}
     * @param zoneId     target IANA or system time zone
     * @param parameters calculation method, Asr rule, high-latitude rule, and minute adjustments
     * @return immutable {@link PrayerTimes} schedule
     */
    public static PrayerTimes calculate(
            GeoCoordinate observer,
            LocalDate civilDate,
            ZoneId zoneId,
            PrayerCalculationParameters parameters
    ) {
        Objects.requireNonNull(observer, "observer must not be null");
        Objects.requireNonNull(civilDate, "civilDate must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");

        // 1. Dhuhr (local solar noon / meridian transit)
        double dhuhrHours = SolarMath.calculateSolarNoonUtcHours(civilDate, observer);
        Instant dhuhrInstant = SolarMath.utcHoursToInstant(civilDate, dhuhrHours);
        PrayerMoment dhuhr = PrayerMoment.astronomical(Prayer.DHUHR, dhuhrInstant);

        // 2. Sunrise and Maghrib (sunset) at standard apparent horizon (-0.833°)
        OptionalDouble sunriseHoursOpt = SolarMath.solarTimeForAltitude(
                civilDate,
                observer,
                SolarMath.APPARENT_SUNRISE_SUNSET_ALTITUDE_DEG,
                true
        );
        OptionalDouble maghribHoursOpt = SolarMath.solarTimeForAltitude(
                civilDate,
                observer,
                SolarMath.APPARENT_SUNRISE_SUNSET_ALTITUDE_DEG,
                false
        );

        PrayerMoment sunrise = sunriseHoursOpt.isPresent()
                ? PrayerMoment.astronomical(Prayer.SUNRISE, SolarMath.utcHoursToInstant(civilDate, sunriseHoursOpt.getAsDouble()))
                : PrayerMoment.unavailable(Prayer.SUNRISE);

        PrayerMoment maghrib = maghribHoursOpt.isPresent()
                ? PrayerMoment.astronomical(Prayer.MAGHRIB, SolarMath.utcHoursToInstant(civilDate, maghribHoursOpt.getAsDouble()))
                : PrayerMoment.unavailable(Prayer.MAGHRIB);

        // 3. Asr (shadow-length altitude in afternoon)
        OptionalDouble asrHoursOpt = SolarMath.solarTimeForAsr(
                civilDate,
                observer,
                parameters.asrMethod().shadowFactor()
        );
        PrayerMoment asr = asrHoursOpt.isPresent()
                ? PrayerMoment.astronomical(Prayer.ASR, SolarMath.utcHoursToInstant(civilDate, asrHoursOpt.getAsDouble()))
                : PrayerMoment.unavailable(Prayer.ASR);

        // 4. Fajr and Isha (require valid sunrise/sunset boundary on non-polar days)
        PrayerMoment fajr = PrayerMoment.unavailable(Prayer.FAJR);
        PrayerMoment isha = PrayerMoment.unavailable(Prayer.ISHA);

        if (sunrise.isAvailable() && maghrib.isAvailable()) {
            Instant sunriseInstant = sunrise.instant().get();
            Instant sunsetInstant = maghrib.instant().get();

            OptionalDouble fajrHoursOpt = SolarMath.solarTimeForAltitude(
                    civilDate,
                    observer,
                    -parameters.fajrAngleDeg(),
                    true
            );
            if (fajrHoursOpt.isPresent()) {
                Instant astroFajr = SolarMath.utcHoursToInstant(civilDate, fajrHoursOpt.getAsDouble());
                if (astroFajr.isBefore(sunriseInstant)) {
                    fajr = PrayerMoment.astronomical(Prayer.FAJR, astroFajr);
                }
            }

            OptionalDouble ishaHoursOpt = SolarMath.solarTimeForAltitude(
                    civilDate,
                    observer,
                    -parameters.ishaAngleDeg(),
                    false
            );
            if (ishaHoursOpt.isPresent()) {
                Instant astroIsha = SolarMath.utcHoursToInstant(civilDate, ishaHoursOpt.getAsDouble());
                if (astroIsha.isAfter(sunsetInstant)) {
                    isha = PrayerMoment.astronomical(Prayer.ISHA, astroIsha);
                }
            }

            // 5. High-latitude safe boundary & fallback (standard Adhan semantics)
            HighLatitudeRule rule = parameters.highLatitudeRule();
            if (rule != HighLatitudeRule.NONE) {
                LocalDate nextCivilDate = civilDate.plusDays(1);
                OptionalDouble nextSunriseHoursOpt = SolarMath.solarTimeForAltitude(
                        nextCivilDate,
                        observer,
                        SolarMath.APPARENT_SUNRISE_SUNSET_ALTITUDE_DEG,
                        true
                );
                if (nextSunriseHoursOpt.isPresent()) {
                    Instant nextSunriseInstant = SolarMath.utcHoursToInstant(nextCivilDate, nextSunriseHoursOpt.getAsDouble());
                    Duration nightDuration = Duration.between(sunsetInstant, nextSunriseInstant);
                    if (!nightDuration.isNegative() && !nightDuration.isZero()) {
                        long nightNanos = nightDuration.toNanos();
                        double fajrFraction = rule.fajrNightFraction(parameters.fajrAngleDeg());
                        Instant safeFajr = sunriseInstant.minusNanos(Math.round(nightNanos * fajrFraction));
                        if (!fajr.isAvailable() || fajr.instant().get().isBefore(safeFajr)) {
                            fajr = PrayerMoment.highLatitudeAdjusted(Prayer.FAJR, safeFajr);
                        }

                        double ishaFraction = rule.ishaNightFraction(parameters.ishaAngleDeg());
                        Instant safeIsha = sunsetInstant.plusNanos(Math.round(nightNanos * ishaFraction));
                        if (!isha.isAvailable() || isha.instant().get().isAfter(safeIsha)) {
                            isha = PrayerMoment.highLatitudeAdjusted(Prayer.ISHA, safeIsha);
                        }
                    }
                }
            }
        }

        // 6. Apply user-configured minute adjustments
        PrayerAdjustments adjustments = parameters.adjustments();
        EnumMap<Prayer, PrayerMoment> moments = new EnumMap<>(Prayer.class);
        moments.put(Prayer.FAJR, fajr.withMinuteAdjustment(adjustments.fajrMinutes()));
        moments.put(Prayer.SUNRISE, sunrise.withMinuteAdjustment(adjustments.sunriseMinutes()));
        moments.put(Prayer.DHUHR, dhuhr.withMinuteAdjustment(adjustments.dhuhrMinutes()));
        moments.put(Prayer.ASR, asr.withMinuteAdjustment(adjustments.asrMinutes()));
        moments.put(Prayer.MAGHRIB, maghrib.withMinuteAdjustment(adjustments.maghribMinutes()));
        moments.put(Prayer.ISHA, isha.withMinuteAdjustment(adjustments.ishaMinutes()));

        return new PrayerTimes(
                civilDate,
                zoneId,
                parameters.method(),
                parameters.asrMethod(),
                parameters.highLatitudeRule(),
                moments
        );
    }
}
