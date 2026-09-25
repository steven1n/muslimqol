package io.github.muslimqol.client;

import io.github.muslimqol.config.ClientConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Handles client-side tooltip rendering for food classifications.
 */
public class FoodTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (ClientConfig.SPEC == null || !ClientConfig.SPEC.isLoaded() || !ClientConfig.SHOW_TOOLTIPS.get()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!FoodClassificationTooltipFormatter.shouldShowTooltip(stack)) {
            return;
        }

        List<Component> lines = FoodClassificationTooltipFormatter.formatTooltip(stack);
        event.getToolTip().addAll(lines);
    }
}
