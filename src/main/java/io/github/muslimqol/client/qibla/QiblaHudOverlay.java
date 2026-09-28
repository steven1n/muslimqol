package io.github.muslimqol.client.qibla;

import io.github.muslimqol.config.ClientConfig;
import io.github.muslimqol.qibla.BearingMath;
import io.github.muslimqol.qibla.QiblaResult;
import io.github.muslimqol.util.ResourceLocationUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.Locale;

/**
 * Minimal, client-only HUD indicator for Qibla direction.
 *
 * <p>Operates completely offline using client-configured observer coordinates and native GUI drawing primitives.
 */
public final class QiblaHudOverlay {

    public static final ResourceLocation LAYER_ID = ResourceLocationUtil.modLoc("qibla_hud");

    public static final int POS_X = 8;
    public static final int POS_Y = 8;
    public static final int BAR_WIDTH = 60;
    public static final int BAR_HEIGHT = 4;

    public static final int COLOR_BACKGROUND = 0x60000000;
    public static final int COLOR_CENTER_NOTCH = 0x80FFFFFF;
    public static final int COLOR_ALIGNED_GREEN = 0xFF55FF55;
    public static final int COLOR_NORMAL_WHITE = 0xFFFFFFFF;
    public static final int COLOR_BEHIND_AMBER = 0xFFFFAA00;
    public static final int COLOR_TEXT_DIM = 0xFFE0E0E0;

    private QiblaHudOverlay() {}

    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, QiblaHudOverlay::render);
    }

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.gui.getDebugOverlay().showDebugScreen()) {
            return;
        }

        if (ClientConfig.SPEC == null || !ClientConfig.SPEC.isLoaded()) {
            return;
        }

        if (!ClientConfig.QIBLA_ENABLED.get()
                || !ClientConfig.QIBLA_HUD_ENABLED.get()
                || !ClientConfig.QIBLA_LOCATION_CONFIGURED.get()) {
            return;
        }

        QiblaResult qiblaResult = QiblaClientService.getCachedQiblaResult();
        if (qiblaResult == null || !qiblaResult.defined()) {
            return;
        }

        float partialTick = deltaTracker != null ? deltaTracker.getGameTimeDeltaPartialTick(false) : 1.0f;
        float playerYaw = minecraft.player.getViewYRot(partialTick);

        QiblaClientState state = QiblaClientService.computeState(
                qiblaResult,
                playerYaw,
                BearingMath.DEFAULT_ALIGNED_TOLERANCE_DEG
        );

        if (!state.available()) {
            return;
        }

        renderHud(guiGraphics, minecraft.font, state);
    }

    public static void renderHud(GuiGraphics guiGraphics, Font font, QiblaClientState state) {
        int x = POS_X;
        int y = POS_Y;

        String bearingStr = String.format(Locale.ROOT, "%.1f°", state.absoluteBearingDeg());
        boolean isBehind = Math.abs(state.relativeAngleDeg()) > 135.0;

        Component displayText;
        int textColor;

        if (state.aligned()) {
            displayText = Component.translatable("hud.muslimqol.qibla", bearingStr)
                    .append(Component.literal(" | "))
                    .append(Component.translatable("hud.muslimqol.qibla.aligned"));
            textColor = COLOR_ALIGNED_GREEN;
        } else if (isBehind) {
            displayText = Component.translatable("hud.muslimqol.qibla", bearingStr)
                    .append(Component.literal(" | "))
                    .append(Component.translatable("hud.muslimqol.qibla.behind"));
            textColor = COLOR_BEHIND_AMBER;
        } else {
            displayText = Component.translatable("hud.muslimqol.qibla", bearingStr);
            textColor = COLOR_TEXT_DIM;
        }

        // Draw bearing text with drop shadow
        guiGraphics.drawString(font, displayText, x, y, textColor, true);

        // Draw relative direction compass strip
        int stripY = y + 11;
        int barCenterX = x + BAR_WIDTH / 2;

        // Strip background
        guiGraphics.fill(x, stripY, x + BAR_WIDTH, stripY + BAR_HEIGHT, COLOR_BACKGROUND);

        // Center alignment guide notch
        guiGraphics.fill(barCenterX - 1, stripY - 1, barCenterX + 1, stripY + BAR_HEIGHT + 1, COLOR_CENTER_NOTCH);

        if (state.aligned()) {
            // Centered green pip
            guiGraphics.fill(barCenterX - 2, stripY - 1, barCenterX + 2, stripY + BAR_HEIGHT + 1, COLOR_ALIGNED_GREEN);
        } else if (isBehind) {
            // Edge warning pips on both sides
            guiGraphics.fill(x, stripY, x + 3, stripY + BAR_HEIGHT, COLOR_BEHIND_AMBER);
            guiGraphics.fill(x + BAR_WIDTH - 3, stripY, x + BAR_WIDTH, stripY + BAR_HEIGHT, COLOR_BEHIND_AMBER);
        } else {
            // Relative moving pip: positive angle (Qibla to right) shifts right; negative shifts left
            double offsetRatio = state.relativeAngleDeg() / 135.0;
            int pipOffset = (int) Math.round(offsetRatio * (BAR_WIDTH / 2 - 4));
            int pipX = barCenterX + pipOffset;
            guiGraphics.fill(pipX - 1, stripY - 1, pipX + 2, stripY + BAR_HEIGHT + 1, COLOR_NORMAL_WHITE);
        }
    }
}
