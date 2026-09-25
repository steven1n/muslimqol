package io.github.muslimqol.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Initializes client-specific features, listeners, and renderers.
 */
public final class ClientInit {

    private ClientInit() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(FoodOverlayRenderer::onRegisterItemDecorations);
        NeoForge.EVENT_BUS.register(FoodTooltipHandler.class);
    }
}
