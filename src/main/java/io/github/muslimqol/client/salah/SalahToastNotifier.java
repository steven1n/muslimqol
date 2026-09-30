package io.github.muslimqol.client.salah;

import io.github.muslimqol.prayer.Prayer;
import io.github.muslimqol.salah.ReminderDecision;
import io.github.muslimqol.salah.ReminderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Client-only Toast notification presenter for obligatory Salah advance and start reminders.
 *
 * <p>Uses standard Minecraft {@link SystemToast} UI mechanisms and localized {@link Component}
 * translation keys. Never sends network packets, chat spam, or OS-level desktop notifications.
 */
public final class SalahToastNotifier {

    private static final long TOAST_DISPLAY_TIME_MS = 5000L;

    private SalahToastNotifier() {}

    /**
     * Builds the localized title {@link Component} for a {@link ReminderDecision}.
     */
    public static Component buildTitleComponent(ReminderDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");
        Prayer prayer = decision.event().prayer();
        Component prayerName = Component.translatable(prayer.translationKey());
        String key = (decision.type() == ReminderType.UPCOMING)
                ? "notification.muslimqol.salah.upcoming.title"
                : "notification.muslimqol.salah.started.title";
        return Component.translatable(key, prayerName);
    }

    /**
     * Builds the localized body {@link Component} for a {@link ReminderDecision}.
     */
    public static Component buildBodyComponent(ReminderDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");
        if (decision.type() == ReminderType.UPCOMING) {
            return Component.translatable(
                    "notification.muslimqol.salah.upcoming.body",
                    decision.advanceMinutes()
            );
        }
        return Component.translatable("notification.muslimqol.salah.started.body");
    }

    /**
     * Displays a client-side Minecraft {@link SystemToast} for the given {@link ReminderDecision}.
     */
    public static void showToast(ReminderDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        ToastComponent toasts = minecraft.getToasts();
        if (toasts == null) {
            return;
        }

        Component title = buildTitleComponent(decision);
        Component body = buildBodyComponent(decision);
        SystemToast.SystemToastId toastId = new SystemToast.SystemToastId(TOAST_DISPLAY_TIME_MS);
        SystemToast.add(toasts, toastId, title, body);
    }
}
