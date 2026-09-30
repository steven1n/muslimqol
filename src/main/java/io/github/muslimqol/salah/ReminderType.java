package io.github.muslimqol.salah;

/**
 * Types of client-side Salah notifications.
 */
public enum ReminderType {
    /**
     * Advance notification triggered {@code advance_notification_minutes} before an obligatory prayer starts.
     */
    UPCOMING,

    /**
     * Notification triggered when an obligatory prayer start time is reached.
     */
    STARTED
}
