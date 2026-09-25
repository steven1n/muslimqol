package io.github.muslimqol.client;

import io.github.muslimqol.api.ConsumptionPolicy;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.config.ClientConfig;
import io.github.muslimqol.food.FoodClassifier;
import net.minecraft.ChatFormatting;
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
        if (!FoodClassifier.isFood(stack)) {
            return;
        }

        FoodClassification classification = FoodClassifier.classify(stack);
        List<Component> tooltip = event.getToolTip();

        ChatFormatting statusColor = switch (classification.status()) {
            case HALAL -> ChatFormatting.GREEN;
            case RESTRICTED -> ChatFormatting.RED;
            case DOUBTFUL -> ChatFormatting.GOLD;
            case UNKNOWN -> ChatFormatting.GRAY;
        };

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable(classification.status().getTranslationKey()).withStyle(statusColor));
        tooltip.add(Component.translatable(classification.getReasonTranslationKey()).withStyle(ChatFormatting.GRAY));

        ConsumptionPolicy policy = FoodClassifier.getPolicy(classification.status());
        if (classification.status() == FoodStatus.RESTRICTED || policy == ConsumptionPolicy.BLOCK) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(
                    "food_policy.muslimqol.policy_line",
                    Component.translatable(policy.getTranslationKey())
            ).withStyle(ChatFormatting.RED));
        } else if (classification.status() == FoodStatus.DOUBTFUL || policy == ConsumptionPolicy.WARN) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(
                    "food_policy.muslimqol.policy_line",
                    Component.translatable(policy.getTranslationKey())
            ).withStyle(ChatFormatting.YELLOW));
        }
    }
}
