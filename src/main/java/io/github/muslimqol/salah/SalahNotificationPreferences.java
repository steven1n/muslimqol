package io.github.muslimqol.salah;

import io.github.muslimqol.prayer.Prayer;

import java.util.Objects;

/**
 * Pure, Minecraft-independent configuration snapshot governing Salah reminder evaluation.
 *
 * <p>Note: {@code advanceNotificationMinutes == 0} explicitly disables advance notifications.
 * Per-prayer toggles affect only reminder notifications and never remove prayers from
 * schedule calculation, {@code previousPrayer}, {@code nextPrayer}, or HUD display.
 */
public record SalahNotificationPreferences(
        boolean prayerEnabled,
        boolean locationConfigured,
        boolean notificationsEnabled,
        boolean advanceNotificationEnabled,
        int advanceNotificationMinutes,
        boolean startNotificationEnabled,
        boolean notifyFajr,
        boolean notifyDhuhr,
        boolean notifyAsr,
        boolean notifyMaghrib,
        boolean notifyIsha
) {

    public static final int MIN_ADVANCE_MINUTES = 0;
    public static final int MAX_ADVANCE_MINUTES = 60;
    public static final int DEFAULT_ADVANCE_MINUTES = 10;

    public SalahNotificationPreferences {
        if (advanceNotificationMinutes < MIN_ADVANCE_MINUTES || advanceNotificationMinutes > MAX_ADVANCE_MINUTES) {
            throw new IllegalArgumentException(
                    "advanceNotificationMinutes must be in [0, 60], got: " + advanceNotificationMinutes
            );
        }
    }

    /**
     * Returns default preferences with all obligatory reminders enabled and a 10-minute advance reminder.
     */
    public static SalahNotificationPreferences defaults() {
        return new SalahNotificationPreferences(
                true,
                true,
                true,
                true,
                DEFAULT_ADVANCE_MINUTES,
                true,
                true,
                true,
                true,
                true,
                true
        );
    }

    /**
     * Returns true if notifications are enabled for the given obligatory prayer.
     * Always returns false for non-obligatory events ({@link Prayer#SUNRISE}).
     */
    public boolean isPrayerNotificationEnabled(Prayer prayer) {
        Objects.requireNonNull(prayer, "prayer must not be null");
        if (!prayer.isObligatoryPrayer()) {
            return false;
        }
        return switch (prayer) {
            case FAJR -> notifyFajr;
            case DHUHR -> notifyDhuhr;
            case ASR -> notifyAsr;
            case MAGHRIB -> notifyMaghrib;
            case ISHA -> notifyIsha;
            case SUNRISE -> false;
        };
    }

    /**
     * Returns true if advance notifications are effectively active ({@code advanceNotificationEnabled}
     * and {@code advanceNotificationMinutes > 0}).
     */
    public boolean isAdvanceReminderActive() {
        return advanceNotificationEnabled && advanceNotificationMinutes > 0;
    }
}
