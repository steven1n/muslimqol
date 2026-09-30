package io.github.muslimqol.salah;

import io.github.muslimqol.prayer.Prayer;
import io.github.muslimqol.prayer.PrayerTimes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure, deterministic, Minecraft-independent reminder engine that evaluates time-crossing
 * transitions for obligatory Salah advance ({@link ReminderType#UPCOMING}) and start
 * ({@link ReminderType#STARTED}) notifications.
 *
 * <p>Key behavioral guarantees:
 * <ul>
 *   <li><b>Obligatory only</b>: {@link Prayer#SUNRISE} and {@code UNAVAILABLE} moments never produce reminders.</li>
 *   <li><b>No startup replay</b>: The first evaluation initializes the poll watermark without replaying past events.</li>
 *   <li><b>Time-crossing window</b>: Reminders trigger when {@code windowStart < triggerInstant <= currentPollInstant}.</li>
 *   <li><b>Bounded catch-up</b>: Forward clock jumps larger than {@link #MAX_CATCH_UP_DURATION} (5 minutes)
 *       clamp {@code windowStart} to {@code currentPollInstant - 5m} so stale reminders are never replayed.</li>
 *   <li><b>Backward clock jump safety</b>: If {@code currentPollInstant < previousPollInstant}, the watermark
 *       resets to {@code currentPollInstant} while preserving delivered keys to prevent duplicate notifications.</li>
 *   <li><b>Session deduplication</b>: Each {@link ReminderKey} fires at most once per session in a bounded set.</li>
 * </ul>
 */
public final class ReminderEngine {

    /**
     * Maximum grace window (5 minutes) for catching up missed reminders after a forward clock jump.
     */
    public static final Duration MAX_CATCH_UP_DURATION = Duration.ofMinutes(5);

    /**
     * Maximum number of delivered {@link ReminderKey} entries retained in memory.
     */
    public static final int MAX_DELIVERED_KEYS = 256;

    private Instant previousPollInstant;
    private final LinkedHashSet<ReminderKey> deliveredKeys = new LinkedHashSet<>();

    /**
     * Evaluates reminders using the engine's internal watermark and delivered-key history.
     *
     * <p>On first evaluation (when no watermark exists), initializes {@code previousPollInstant}
     * to {@code currentPollInstant} and returns an empty list without replaying earlier events.
     */
    public synchronized List<ReminderDecision> evaluate(
            Instant currentPollInstant,
            List<PrayerTimes> schedules,
            SalahNotificationPreferences preferences
    ) {
        Objects.requireNonNull(currentPollInstant, "currentPollInstant must not be null");
        Objects.requireNonNull(schedules, "schedules must not be null");
        Objects.requireNonNull(preferences, "preferences must not be null");

        if (!preferences.prayerEnabled() || !preferences.locationConfigured()) {
            previousPollInstant = null;
            return List.of();
        }

        if (previousPollInstant == null) {
            previousPollInstant = currentPollInstant;
            return List.of();
        }

        return evaluateInternal(previousPollInstant, currentPollInstant, schedules, preferences);
    }

    /**
     * Evaluates reminders across an explicit {@code (previousPoll, currentPoll]} interval while
     * updating the engine's internal watermark and bounded delivered-key history.
     */
    public synchronized List<ReminderDecision> evaluate(
            Instant previousPoll,
            Instant currentPoll,
            List<PrayerTimes> schedules,
            SalahNotificationPreferences preferences
    ) {
        Objects.requireNonNull(currentPoll, "currentPoll must not be null");
        Objects.requireNonNull(schedules, "schedules must not be null");
        Objects.requireNonNull(preferences, "preferences must not be null");

        if (!preferences.prayerEnabled() || !preferences.locationConfigured()) {
            this.previousPollInstant = null;
            return List.of();
        }

        if (previousPoll == null) {
            this.previousPollInstant = currentPoll;
            return List.of();
        }

        return evaluateInternal(previousPoll, currentPoll, schedules, preferences);
    }

    private List<ReminderDecision> evaluateInternal(
            Instant prevPoll,
            Instant currentPoll,
            List<PrayerTimes> schedules,
            SalahNotificationPreferences preferences
    ) {
        // Backward clock jump: reset watermark safely without clearing delivered keys
        if (currentPoll.isBefore(prevPoll)) {
            this.previousPollInstant = currentPoll;
            return List.of();
        }

        this.previousPollInstant = currentPoll;

        if (currentPoll.equals(prevPoll) || !preferences.notificationsEnabled()) {
            return List.of();
        }

        Instant earliestCatchUp = currentPoll.minus(MAX_CATCH_UP_DURATION);
        Instant windowStart = prevPoll.isBefore(earliestCatchUp) ? earliestCatchUp : prevPoll;

        List<SalahEvent> obligatoryEvents = new ArrayList<>();
        for (PrayerTimes schedule : schedules) {
            if (schedule == null) {
                continue;
            }
            for (Prayer prayer : SalahScheduleService.OBLIGATORY_PRAYERS) {
                SalahEvent.fromSchedule(schedule, prayer).ifPresent(obligatoryEvents::add);
            }
        }
        obligatoryEvents.sort(Comparator.comparing(SalahEvent::instant));

        List<ReminderDecision> decisions = new ArrayList<>();
        int advanceMinutes = preferences.advanceNotificationMinutes();
        boolean checkAdvance = preferences.isAdvanceReminderActive();
        boolean checkStart = preferences.startNotificationEnabled();

        for (SalahEvent event : obligatoryEvents) {
            Prayer prayer = event.prayer();
            if (!preferences.isPrayerNotificationEnabled(prayer)) {
                continue;
            }

            Instant prayerInstant = event.instant();

            if (checkAdvance && currentPoll.isBefore(prayerInstant)) {
                Instant upcomingTrigger = prayerInstant.minus(Duration.ofMinutes(advanceMinutes));
                if (isCrossed(windowStart, currentPoll, upcomingTrigger)) {
                    ReminderKey key = new ReminderKey(
                            event.civilDate(),
                            prayer,
                            ReminderType.UPCOMING,
                            prayerInstant
                    );
                    if (markDelivered(key)) {
                        decisions.add(new ReminderDecision(
                                key,
                                event,
                                ReminderType.UPCOMING,
                                upcomingTrigger,
                                advanceMinutes
                        ));
                    }
                }
            }

            if (checkStart) {
                if (isCrossed(windowStart, currentPoll, prayerInstant)) {
                    ReminderKey key = new ReminderKey(
                            event.civilDate(),
                            prayer,
                            ReminderType.STARTED,
                            prayerInstant
                    );
                    if (markDelivered(key)) {
                        decisions.add(new ReminderDecision(
                                key,
                                event,
                                ReminderType.STARTED,
                                prayerInstant,
                                0
                        ));
                    }
                }
            }
        }

        decisions.sort(Comparator.comparing(ReminderDecision::triggerInstant));
        return List.copyOf(decisions);
    }

    private static boolean isCrossed(Instant windowStart, Instant currentPoll, Instant triggerInstant) {
        return triggerInstant.isAfter(windowStart) && !triggerInstant.isAfter(currentPoll);
    }

    private boolean markDelivered(ReminderKey key) {
        if (deliveredKeys.contains(key)) {
            return false;
        }
        deliveredKeys.add(key);
        while (deliveredKeys.size() > MAX_DELIVERED_KEYS) {
            Iterator<ReminderKey> it = deliveredKeys.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        return true;
    }

    /**
     * Resets the poll watermark so the next evaluation initializes at the current instant without
     * replaying historical reminders (used when schedule configuration changes).
     * Retains delivered keys for deduplication safety.
     */
    public synchronized void resetWatermark() {
        this.previousPollInstant = null;
    }

    /**
     * Clears both the poll watermark and all delivered reminder keys.
     */
    public synchronized void resetAll() {
        this.previousPollInstant = null;
        this.deliveredKeys.clear();
    }

    /**
     * Returns the current watermark instant, if initialized.
     */
    public synchronized Optional<Instant> previousPollInstant() {
        return Optional.ofNullable(previousPollInstant);
    }

    /**
     * Returns an immutable snapshot of currently retained delivered reminder keys.
     */
    public synchronized Set<ReminderKey> deliveredKeys() {
        return Set.copyOf(deliveredKeys);
    }
}
