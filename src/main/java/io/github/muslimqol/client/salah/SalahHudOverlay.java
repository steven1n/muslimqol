package io.github.muslimqol.client.salah;

import io.github.muslimqol.client.qibla.QiblaClientService;
import io.github.muslimqol.config.ClientConfig;
import io.github.muslimqol.qibla.QiblaResult;
import io.github.muslimqol.salah.CountdownValue;
import io.github.muslimqol.util.ResourceLocationUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.Objects;

/**
 * Minimal, client-only HUD indicator displaying the next obligatory prayer, its local time in the
 * configured prayer {@code ZoneId}, and the remaining countdown.
 *
 * <p>Coexists deterministically with {@link io.github.muslimqol.client.qibla.QiblaHudOverlay}:
 * renders at {@code y = 28} when the Qibla HUD is visible, or at {@code y = 8} when the Qibla HUD
 * is hidden. Consumes pre-computed immutable {@link SalahHudState} from {@link SalahClientService}
 * without running solar calculations on the render thread.
 */
public final class SalahHudOverlay {

    public static final ResourceLocation LAYER_ID = ResourceLocationUtil.modLoc("salah_hud");

    public static final int POS_X = 8;
    public static final int POS_Y_STANDALONE = 8;
    public static final int POS_Y_BELOW_QIBLA = 28;
    public static final int LINE_SPACING = 10;

    public static final int COLOR_PRIMARY_TEXT = 0xFFE0E0E0;
    public static final int COLOR_COUNTDOWN_TEXT = 0xFFBBBBBB;

    private SalahHudOverlay() {}

    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, SalahHudOverlay::render);
    }

    /**
     * Resolves the vertical Y coordinate for the Salah HUD based on whether the Qibla HUD is visible.
     */
    public static int resolvePosY(boolean qiblaHudVisible) {
        return qiblaHudVisible ? POS_Y_BELOW_QIBLA : POS_Y_STANDALONE;
    }

    /**
     * Checks whether the Qibla HUD overlay is currently active and eligible to render.
     */
    public static boolean isQiblaHudVisible() {
        if (ClientConfig.SPEC == null || !ClientConfig.SPEC.isLoaded()) {
            return false;
        }
        if (!ClientConfig.QIBLA_ENABLED.get()
                || !ClientConfig.QIBLA_HUD_ENABLED.get()
                || !ClientConfig.QIBLA_LOCATION_CONFIGURED.get()) {
            return false;
        }
        QiblaResult qiblaResult = QiblaClientService.getCachedQiblaResult();
        return qiblaResult != null && qiblaResult.defined();
    }

    /**
     * Builds the localized primary HUD line (e.g., {@code "Next: Asr 16:37"}).
     */
    public static Component buildNextPrayerLine(SalahHudState state) {
        Objects.requireNonNull(state, "state must not be null");
        Component prayerName = Component.translatable(state.nextPrayer().translationKey());
        return Component.translatable("hud.muslimqol.salah.next", prayerName, state.formattedLocalTime());
    }

    /**
     * Builds the localized duration {@link Component} from a language-neutral {@link CountdownValue}.
     */
    public static Component buildDurationComponent(CountdownValue countdown) {
        Objects.requireNonNull(countdown, "countdown must not be null");
        if (countdown.lessThanOneMinute()) {
            return Component.translatable("hud.muslimqol.salah.duration.less_than_minute");
        }
        if (countdown.hours() == 0L) {
            return Component.translatable("hud.muslimqol.salah.duration.minutes", countdown.minutes());
        }
        if (countdown.minutes() == 0L) {
            return Component.translatable("hud.muslimqol.salah.duration.hours", countdown.hours());
        }
        return Component.translatable(
                "hud.muslimqol.salah.duration.hours_minutes",
                countdown.hours(),
                countdown.zeroPaddedMinutes()
        );
    }

    /**
     * Builds the localized countdown HUD line (e.g., {@code "in 5h 20m"} in English or
     * {@code "خلال 5 ساعة و20 دقيقة"} in Arabic).
     */
    public static Component buildCountdownLine(SalahHudState state) {
        Objects.requireNonNull(state, "state must not be null");
        Component durationComponent = buildDurationComponent(state.countdown());
        return Component.translatable("hud.muslimqol.salah.countdown", durationComponent);
    }

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.gui.getDebugOverlay().showDebugScreen()) {
            return;
        }

        if (ClientConfig.SPEC == null || !ClientConfig.SPEC.isLoaded()) {
            return;
        }

        if (!ClientConfig.PRAYER_ENABLED.get()
                || !ClientConfig.QIBLA_LOCATION_CONFIGURED.get()
                || !ClientConfig.PRAYER_SALAH_HUD_ENABLED.get()) {
            return;
        }

        SalahHudState state = SalahClientService.getCachedHudState();
        if (state == null || !state.visible()) {
            return;
        }

        int posY = resolvePosY(isQiblaHudVisible());
        renderHud(guiGraphics, minecraft.font, state, POS_X, posY);
    }

    public static void renderHud(GuiGraphics guiGraphics, Font font, SalahHudState state, int x, int y) {
        Component nextLine = buildNextPrayerLine(state);
        Component countdownLine = buildCountdownLine(state);

        guiGraphics.drawString(font, nextLine, x, y, COLOR_PRIMARY_TEXT, true);
        guiGraphics.drawString(font, countdownLine, x, y + LINE_SPACING, COLOR_COUNTDOWN_TEXT, true);
    }
}
