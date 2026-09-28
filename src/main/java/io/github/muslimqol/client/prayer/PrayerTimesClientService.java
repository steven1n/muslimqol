package io.github.muslimqol.client.prayer;

import io.github.muslimqol.MuslimQolMod;
import io.github.muslimqol.config.ClientConfig;
import io.github.muslimqol.prayer.AsrMethod;
import io.github.muslimqol.prayer.CalculationMethod;
import io.github.muslimqol.prayer.HighLatitudeRule;
import io.github.muslimqol.prayer.PrayerAdjustments;
import io.github.muslimqol.prayer.PrayerCalculationParameters;
import io.github.muslimqol.prayer.PrayerTimes;
import io.github.muslimqol.prayer.PrayerTimesCalculator;
import io.github.muslimqol.qibla.GeoCoordinate;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-side service that resolves client prayer configuration, caches the daily {@link PrayerTimes}
 * schedule, and invalidates automatically when the date, observer coordinates, time zone, or
 * calculation parameters change.
 *
 * <p>Privacy invariant: Observer coordinates are read strictly from client configuration and are
 * never logged or transmitted over the network.
 */
public final class PrayerTimesClientService {

    private record CacheKey(
            LocalDate civilDate,
            double latitudeDeg,
            double longitudeDeg,
            ZoneId zoneId,
            PrayerCalculationParameters parameters
    ) {}

    private static Clock activeClock = Clock.systemDefaultZone();
    private static CacheKey lastCacheKey = null;
    private static PrayerTimes cachedSchedule = null;
    private static String lastLoggedWarningKey = null;

    private PrayerTimesClientService() {}

    /**
     * Sets the clock used for determining the current civil date in the resolved time zone.
     */
    public static synchronized void setClock(Clock clock) {
        activeClock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Resets the clock to {@link Clock#systemDefaultZone()} and clears cached state.
     */
    public static synchronized void resetCache() {
        activeClock = Clock.systemDefaultZone();
        lastCacheKey = null;
        cachedSchedule = null;
        lastLoggedWarningKey = null;
    }

    /**
     * Resolves an IANA time zone string, falling back to {@code defaultZone} when blank,
     * or returning {@link Optional#empty()} if the zone ID is malformed.
     */
    public static Optional<ZoneId> resolveZoneId(String configuredZoneId, ZoneId defaultZone) {
        Objects.requireNonNull(defaultZone, "defaultZone must not be null");
        if (configuredZoneId == null || configuredZoneId.isBlank()) {
            return Optional.of(defaultZone);
        }
        String trimmed = configuredZoneId.trim();
        try {
            return Optional.of(ZoneId.of(trimmed));
        } catch (DateTimeException e) {
            logWarningOnce("zone:" + trimmed, "Invalid prayer.zone_id '" + trimmed + "'; prayer schedule unavailable.");
            return Optional.empty();
        }
    }

    /**
     * Parses raw configuration values into validated {@link PrayerCalculationParameters},
     * returning {@link Optional#empty()} if any setting is malformed or out of bounds.
     */
    public static Optional<PrayerCalculationParameters> resolveParameters(
            String methodRaw,
            double customFajrAngleDeg,
            double customIshaAngleDeg,
            String asrMethodRaw,
            String highLatRuleRaw,
            int fajrAdj,
            int sunriseAdj,
            int dhuhrAdj,
            int asrAdj,
            int maghribAdj,
            int ishaAdj
    ) {
        Optional<CalculationMethod> methodOpt = CalculationMethod.fromConfigName(methodRaw);
        if (methodOpt.isEmpty()) {
            logWarningOnce("method:" + methodRaw, "Invalid prayer.calculation_method '" + methodRaw + "'; prayer schedule unavailable.");
            return Optional.empty();
        }

        Optional<AsrMethod> asrOpt = AsrMethod.fromConfigName(asrMethodRaw);
        if (asrOpt.isEmpty()) {
            logWarningOnce("asr:" + asrMethodRaw, "Invalid prayer.asr_method '" + asrMethodRaw + "'; prayer schedule unavailable.");
            return Optional.empty();
        }

        Optional<HighLatitudeRule> ruleOpt = HighLatitudeRule.fromConfigName(highLatRuleRaw);
        if (ruleOpt.isEmpty()) {
            logWarningOnce("rule:" + highLatRuleRaw, "Invalid prayer.high_latitude_rule '" + highLatRuleRaw + "'; prayer schedule unavailable.");
            return Optional.empty();
        }

        try {
            PrayerAdjustments adjustments = new PrayerAdjustments(
                    fajrAdj,
                    sunriseAdj,
                    dhuhrAdj,
                    asrAdj,
                    maghribAdj,
                    ishaAdj
            );
            CalculationMethod method = methodOpt.get();
            PrayerCalculationParameters params = (method == CalculationMethod.CUSTOM)
                    ? PrayerCalculationParameters.custom(customFajrAngleDeg, customIshaAngleDeg, asrOpt.get(), ruleOpt.get(), adjustments)
                    : PrayerCalculationParameters.of(method, asrOpt.get(), ruleOpt.get(), adjustments);
            return Optional.of(params);
        } catch (IllegalArgumentException e) {
            logWarningOnce("bounds:" + e.getMessage(), "Invalid prayer calculation parameter bounds (" + e.getMessage() + "); prayer schedule unavailable.");
            return Optional.empty();
        }
    }

    /**
     * Evaluates and caches the daily prayer schedule for the given explicit configuration snapshot and clock.
     */
    public static synchronized Optional<PrayerTimes> evaluateAndCache(
            boolean prayerEnabled,
            boolean locationConfigured,
            double latitudeDeg,
            double longitudeDeg,
            String configuredZoneId,
            String methodRaw,
            double customFajrAngleDeg,
            double customIshaAngleDeg,
            String asrMethodRaw,
            String highLatRuleRaw,
            int fajrAdj,
            int sunriseAdj,
            int dhuhrAdj,
            int asrAdj,
            int maghribAdj,
            int ishaAdj,
            Clock clock
    ) {
        Objects.requireNonNull(clock, "clock must not be null");

        if (!prayerEnabled || !locationConfigured) {
            lastCacheKey = null;
            cachedSchedule = null;
            return Optional.empty();
        }

        GeoCoordinate observer;
        try {
            observer = GeoCoordinate.of(latitudeDeg, longitudeDeg);
        } catch (IllegalArgumentException e) {
            logWarningOnce("coord_bounds", "Configured observer coordinates are out of valid geographic bounds; prayer schedule unavailable.");
            lastCacheKey = null;
            cachedSchedule = null;
            return Optional.empty();
        }

        Optional<ZoneId> zoneOpt = resolveZoneId(configuredZoneId, clock.getZone());
        if (zoneOpt.isEmpty()) {
            lastCacheKey = null;
            cachedSchedule = null;
            return Optional.empty();
        }

        Optional<PrayerCalculationParameters> paramsOpt = resolveParameters(
                methodRaw,
                customFajrAngleDeg,
                customIshaAngleDeg,
                asrMethodRaw,
                highLatRuleRaw,
                fajrAdj,
                sunriseAdj,
                dhuhrAdj,
                asrAdj,
                maghribAdj,
                ishaAdj
        );
        if (paramsOpt.isEmpty()) {
            lastCacheKey = null;
            cachedSchedule = null;
            return Optional.empty();
        }

        ZoneId zoneId = zoneOpt.get();
        PrayerCalculationParameters parameters = paramsOpt.get();
        LocalDate civilDate = LocalDate.ofInstant(clock.instant(), zoneId);

        CacheKey key = new CacheKey(civilDate, latitudeDeg, longitudeDeg, zoneId, parameters);
        if (cachedSchedule != null && key.equals(lastCacheKey)) {
            return Optional.of(cachedSchedule);
        }

        PrayerTimes computed = PrayerTimesCalculator.calculate(observer, civilDate, zoneId, parameters);
        lastCacheKey = key;
        cachedSchedule = computed;
        lastLoggedWarningKey = null;
        return Optional.of(computed);
    }

    /**
     * Retrieves today's cached {@link PrayerTimes} schedule using live {@link ClientConfig} values.
     */
    public static synchronized Optional<PrayerTimes> getTodaySchedule() {
        if (ClientConfig.SPEC == null || !ClientConfig.SPEC.isLoaded()) {
            return Optional.empty();
        }

        return evaluateAndCache(
                ClientConfig.PRAYER_ENABLED.get(),
                ClientConfig.QIBLA_LOCATION_CONFIGURED.get(),
                ClientConfig.QIBLA_LATITUDE.get(),
                ClientConfig.QIBLA_LONGITUDE.get(),
                ClientConfig.PRAYER_ZONE_ID.get(),
                ClientConfig.PRAYER_CALCULATION_METHOD.get(),
                ClientConfig.PRAYER_CUSTOM_FAJR_ANGLE.get(),
                ClientConfig.PRAYER_CUSTOM_ISHA_ANGLE.get(),
                ClientConfig.PRAYER_ASR_METHOD.get(),
                ClientConfig.PRAYER_HIGH_LATITUDE_RULE.get(),
                ClientConfig.PRAYER_ADJUST_FAJR.get(),
                ClientConfig.PRAYER_ADJUST_SUNRISE.get(),
                ClientConfig.PRAYER_ADJUST_DHUHR.get(),
                ClientConfig.PRAYER_ADJUST_ASR.get(),
                ClientConfig.PRAYER_ADJUST_MAGHRIB.get(),
                ClientConfig.PRAYER_ADJUST_ISHA.get(),
                activeClock
        );
    }

    private static void logWarningOnce(String key, String message) {
        if (!Objects.equals(lastLoggedWarningKey, key)) {
            lastLoggedWarningKey = key;
            MuslimQolMod.LOGGER.warn(message);
        }
    }
}
