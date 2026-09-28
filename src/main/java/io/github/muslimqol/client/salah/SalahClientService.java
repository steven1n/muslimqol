package io.github.muslimqol.client.salah;

import io.github.muslimqol.client.prayer.PrayerTimesClientService;
import io.github.muslimqol.config.ClientConfig;
import io.github.muslimqol.prayer.PrayerCalculationParameters;
import io.github.muslimqol.prayer.PrayerTimes;
import io.github.muslimqol.prayer.PrayerTimesCalculator;
import io.github.muslimqol.qibla.GeoCoordinate;
import io.github.muslimqol.salah.ReminderDecision;
import io.github.muslimqol.salah.ReminderEngine;
import io.github.muslimqol.salah.SalahNotificationPreferences;
import io.github.muslimqol.salah.SalahScheduleService;
import io.github.muslimqol.salah.SalahScheduleState;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Client-side service that coordinates adjacent-day {@link PrayerTimes} schedules, derives
 * {@link SalahScheduleState} and immutable {@link SalahHudState}, evaluates {@link ReminderEngine}
 * on a lightweight 1-second client tick cadence, and dispatches client-only Toast notifications.
 *
 * <p>Privacy invariant: Observer coordinates are read strictly from client configuration and are
 * never logged, stored on a server, or transmitted over the network.
 */
public final class SalahClientService {

    private record ScheduleConfigKey(
            double latitudeDeg,
            double longitudeDeg,
            ZoneId zoneId,
            PrayerCalculationParameters parameters
    ) {}

    private record ScheduleWindowCacheKey(
            LocalDate todayDate,
            ScheduleConfigKey configKey
    ) {}

    private record AdjacentSchedules(
            PrayerTimes yesterday,
            PrayerTimes today,
            PrayerTimes tomorrow
    ) {
        List<PrayerTimes> asList() {
            return List.of(yesterday, today, tomorrow);
        }
    }

    /**
     * Immutable result of a single client poll evaluation.
     */
    public record PollEvaluationResult(
            SalahScheduleState scheduleState,
            SalahHudState hudState,
            List<ReminderDecision> triggeredReminders
    ) {}

    private static Clock activeClock = Clock.systemDefaultZone();
    private static final ReminderEngine reminderEngine = new ReminderEngine();
    private static Consumer<ReminderDecision> reminderDispatcher = SalahToastNotifier::showToast;

    private static ScheduleConfigKey lastConfigKey = null;
    private static ScheduleWindowCacheKey lastWindowKey = null;
    private static AdjacentSchedules cachedSchedules = null;

    private static long lastPolledEpochSecond = Long.MIN_VALUE;
    private static SalahScheduleState cachedScheduleState = null;
    private static SalahHudState cachedHudState = SalahHudState.hidden();

    private SalahClientService() {}

    /**
     * Sets the clock used by the client service (for deterministic testing).
     */
    public static synchronized void setClock(Clock clock) {
        activeClock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Sets the consumer invoked when a reminder decision is triggered.
     */
    public static synchronized void setReminderDispatcher(Consumer<ReminderDecision> dispatcher) {
        reminderDispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    }

    /**
     * Resets the clock, reminder engine state, and cached schedules to defaults.
     */
    public static synchronized void resetState() {
        activeClock = Clock.systemDefaultZone();
        reminderEngine.resetAll();
        reminderDispatcher = SalahToastNotifier::showToast;
        lastConfigKey = null;
        lastWindowKey = null;
        cachedSchedules = null;
        lastPolledEpochSecond = Long.MIN_VALUE;
        cachedScheduleState = null;
        cachedHudState = SalahHudState.hidden();
    }

    /**
     * Client tick listener registered on the NeoForge game event bus.
     * Evaluates schedule state and reminders at most once per second (or immediately if a prayer start is reached).
     */
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ClientConfig.SPEC == null || !ClientConfig.SPEC.isLoaded()) {
            return;
        }

        Instant now = activeClock.instant();
        long epochSecond = now.getEpochSecond();
        boolean dueReached = cachedScheduleState != null
                && cachedScheduleState.nextPrayer().isPresent()
                && !now.isBefore(cachedScheduleState.nextPrayer().get().instant());

        if (epochSecond == lastPolledEpochSecond && cachedScheduleState != null && !dueReached) {
            return;
        }

        pollFromConfig();
    }

    /**
     * Evaluates the current Salah state and reminders immediately using live {@link ClientConfig} values.
     */
    public static synchronized PollEvaluationResult pollFromConfig() {
        if (ClientConfig.SPEC == null || !ClientConfig.SPEC.isLoaded()) {
            Instant now = activeClock.instant();
            SalahScheduleState unavailable = SalahScheduleState.unavailable(now);
            cachedScheduleState = unavailable;
            cachedHudState = SalahHudState.hidden();
            return new PollEvaluationResult(unavailable, SalahHudState.hidden(), List.of());
        }

        int rawAdvanceMinutes = ClientConfig.PRAYER_ADVANCE_NOTIFICATION_MINUTES.get();
        int clampedAdvanceMinutes = Math.max(
                SalahNotificationPreferences.MIN_ADVANCE_MINUTES,
                Math.min(SalahNotificationPreferences.MAX_ADVANCE_MINUTES, rawAdvanceMinutes)
        );

        SalahNotificationPreferences preferences = new SalahNotificationPreferences(
                ClientConfig.PRAYER_ENABLED.get(),
                ClientConfig.QIBLA_LOCATION_CONFIGURED.get(),
                ClientConfig.PRAYER_NOTIFICATIONS_ENABLED.get(),
                ClientConfig.PRAYER_ADVANCE_NOTIFICATION_ENABLED.get(),
                clampedAdvanceMinutes,
                ClientConfig.PRAYER_START_NOTIFICATION_ENABLED.get(),
                ClientConfig.PRAYER_NOTIFY_FAJR.get(),
                ClientConfig.PRAYER_NOTIFY_DHUHR.get(),
                ClientConfig.PRAYER_NOTIFY_ASR.get(),
                ClientConfig.PRAYER_NOTIFY_MAGHRIB.get(),
                ClientConfig.PRAYER_NOTIFY_ISHA.get()
        );

        return evaluate(
                ClientConfig.PRAYER_SALAH_HUD_ENABLED.get(),
                preferences,
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

    /**
     * Evaluates Salah schedule state, HUD state, and reminders for an explicit configuration snapshot and clock.
     */
    public static synchronized PollEvaluationResult evaluate(
            boolean salahHudEnabled,
            SalahNotificationPreferences preferences,
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
        Objects.requireNonNull(preferences, "preferences must not be null");
        Objects.requireNonNull(clock, "clock must not be null");

        Instant now = clock.instant();
        lastPolledEpochSecond = now.getEpochSecond();

        if (!preferences.prayerEnabled() || !preferences.locationConfigured()) {
            clearCachedSchedulesAndResetWatermark();
            SalahScheduleState unavailable = SalahScheduleState.unavailable(now);
            cachedScheduleState = unavailable;
            cachedHudState = SalahHudState.hidden();
            return new PollEvaluationResult(unavailable, SalahHudState.hidden(), List.of());
        }

        GeoCoordinate observer;
        try {
            observer = GeoCoordinate.of(latitudeDeg, longitudeDeg);
        } catch (IllegalArgumentException e) {
            clearCachedSchedulesAndResetWatermark();
            SalahScheduleState unavailable = SalahScheduleState.unavailable(now);
            cachedScheduleState = unavailable;
            cachedHudState = SalahHudState.hidden();
            return new PollEvaluationResult(unavailable, SalahHudState.hidden(), List.of());
        }

        Optional<ZoneId> zoneOpt = PrayerTimesClientService.resolveZoneId(configuredZoneId, clock.getZone());
        if (zoneOpt.isEmpty()) {
            clearCachedSchedulesAndResetWatermark();
            SalahScheduleState unavailable = SalahScheduleState.unavailable(now);
            cachedScheduleState = unavailable;
            cachedHudState = SalahHudState.hidden();
            return new PollEvaluationResult(unavailable, SalahHudState.hidden(), List.of());
        }

        Optional<PrayerCalculationParameters> paramsOpt = PrayerTimesClientService.resolveParameters(
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
            clearCachedSchedulesAndResetWatermark();
            SalahScheduleState unavailable = SalahScheduleState.unavailable(now);
            cachedScheduleState = unavailable;
            cachedHudState = SalahHudState.hidden();
            return new PollEvaluationResult(unavailable, SalahHudState.hidden(), List.of());
        }

        ZoneId zoneId = zoneOpt.get();
        PrayerCalculationParameters parameters = paramsOpt.get();
        LocalDate todayDate = LocalDate.ofInstant(now, zoneId);

        ScheduleConfigKey configKey = new ScheduleConfigKey(latitudeDeg, longitudeDeg, zoneId, parameters);
        if (!configKey.equals(lastConfigKey)) {
            // Schedule configuration changed: reset reminder watermark so we don't fire stale/retroactive reminders
            lastConfigKey = configKey;
            reminderEngine.resetWatermark();
        }

        ScheduleWindowCacheKey windowKey = new ScheduleWindowCacheKey(todayDate, configKey);
        if (cachedSchedules == null || !windowKey.equals(lastWindowKey)) {
            PrayerTimes yesterday = PrayerTimesCalculator.calculate(observer, todayDate.minusDays(1), zoneId, parameters);
            PrayerTimes today = PrayerTimesCalculator.calculate(observer, todayDate, zoneId, parameters);
            PrayerTimes tomorrow = PrayerTimesCalculator.calculate(observer, todayDate.plusDays(1), zoneId, parameters);
            cachedSchedules = new AdjacentSchedules(yesterday, today, tomorrow);
            lastWindowKey = windowKey;
        }

        List<PrayerTimes> scheduleList = cachedSchedules.asList();
        SalahScheduleState scheduleState = SalahScheduleService.resolve(now, scheduleList);
        SalahHudState hudState = SalahHudState.fromScheduleState(salahHudEnabled, scheduleState);

        List<ReminderDecision> decisions = reminderEngine.evaluate(now, scheduleList, preferences);
        for (ReminderDecision decision : decisions) {
            reminderDispatcher.accept(decision);
        }

        cachedScheduleState = scheduleState;
        cachedHudState = hudState;
        return new PollEvaluationResult(scheduleState, hudState, decisions);
    }

    private static void clearCachedSchedulesAndResetWatermark() {
        lastConfigKey = null;
        lastWindowKey = null;
        cachedSchedules = null;
        reminderEngine.resetWatermark();
    }

    /**
     * Returns the most recently prepared immutable {@link SalahHudState} for HUD rendering.
     * Never triggers astronomical calculation on the render thread.
     */
    public static synchronized SalahHudState getCachedHudState() {
        return cachedHudState;
    }

    /**
     * Returns the most recently resolved {@link SalahScheduleState}, if available.
     */
    public static synchronized Optional<SalahScheduleState> getCachedScheduleState() {
        return Optional.ofNullable(cachedScheduleState);
    }
}
