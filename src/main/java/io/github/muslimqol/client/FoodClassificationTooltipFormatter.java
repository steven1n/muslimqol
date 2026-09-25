package io.github.muslimqol.client;

import io.github.muslimqol.api.ConsumptionPolicy;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.food.BuiltinFoodData;
import io.github.muslimqol.food.FoodClassifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared client presentation helper for formatting food classification tooltips.
 *
 * <p>Used by NeoForge's {@code ItemTooltipEvent} to populate inventory, hotbar, and any recipe
 * viewer that obtains {@code ItemStack} tooltips through the standard Minecraft/NeoForge pipeline.
 *
 * <p><strong>ResourceLocation vs ItemStack:</strong> {@link #shouldShowTooltip(ItemStack)} checks
 * {@code DataComponents.FOOD} and can therefore detect any modded edible item.
 * {@link #shouldShowTooltip(ResourceLocation)} cannot access DataComponents and will return
 * {@code true} only for items that are explicitly present in MuslimQoL classification data.
 * For actual UI rendering always prefer the {@code ItemStack} overload.
 */
public final class FoodClassificationTooltipFormatter {

    private FoodClassificationTooltipFormatter() {}

    /**
     * Determines whether the given item stack is a valid food that should display MuslimQoL status UI.
     * Returns {@code false} for non-food items (e.g. stone, dirt) to prevent tooltip clutter.
     */
    public static boolean shouldShowTooltip(ItemStack stack) {
        return stack != null && !stack.isEmpty() && FoodClassifier.isFood(stack);
    }

    /**
     * Determines whether an item identified by ResourceLocation is considered food or explicitly classified.
     */
    public static boolean shouldShowTooltip(ResourceLocation itemId) {
        if (itemId == null) {
            return false;
        }
        FoodClassification classification = FoodClassifier.classify(itemId);
        return classification.status() != FoodStatus.UNKNOWN || BuiltinFoodData.getClassification(itemId).isPresent();
    }

    /**
     * Formats concise, player-facing classification tooltip lines for an item stack.
     * Returns an empty list if the item is not a food.
     * Does NOT expose debug or diagnostic information (such as providerId, priority, or ruleId).
     */
    public static List<Component> formatTooltip(ItemStack stack) {
        if (!shouldShowTooltip(stack)) {
            return Collections.emptyList();
        }

        FoodClassification classification = FoodClassifier.classify(stack);
        return formatTooltip(classification);
    }

    /**
     * Formats concise, player-facing classification tooltip lines for an item identifier.
     * Returns an empty list if the item is not a food.
     */
    public static List<Component> formatTooltip(ResourceLocation itemId) {
        if (!shouldShowTooltip(itemId)) {
            return Collections.emptyList();
        }

        FoodClassification classification = FoodClassifier.classify(itemId);
        return formatTooltip(classification);
    }

    /**
     * Formats concise tooltip components for a resolved FoodClassification.
     */
    public static List<Component> formatTooltip(FoodClassification classification) {
        if (classification == null) {
            return Collections.emptyList();
        }

        List<Component> lines = new ArrayList<>();
        ChatFormatting statusColor = getStatusColor(classification.status());

        lines.add(Component.empty());
        lines.add(Component.translatable(classification.status().getTranslationKey()).withStyle(statusColor));
        lines.add(Component.translatable(classification.getReasonTranslationKey()).withStyle(ChatFormatting.GRAY));

        ConsumptionPolicy policy = FoodClassifier.getPolicy(classification.status());
        if (classification.status() == FoodStatus.RESTRICTED || policy == ConsumptionPolicy.BLOCK) {
            lines.add(Component.empty());
            lines.add(Component.translatable(
                    "food_policy.muslimqol.policy_line",
                    Component.translatable(policy.getTranslationKey())
            ).withStyle(ChatFormatting.RED));
        } else if (classification.status() == FoodStatus.DOUBTFUL || policy == ConsumptionPolicy.WARN) {
            lines.add(Component.empty());
            lines.add(Component.translatable(
                    "food_policy.muslimqol.policy_line",
                    Component.translatable(policy.getTranslationKey())
            ).withStyle(ChatFormatting.YELLOW));
        }

        return Collections.unmodifiableList(lines);
    }

    /**
     * Returns the theme color associated with a food status.
     */
    public static ChatFormatting getStatusColor(FoodStatus status) {
        if (status == null) {
            return ChatFormatting.GRAY;
        }
        return switch (status) {
            case HALAL -> ChatFormatting.GREEN;
            case RESTRICTED -> ChatFormatting.RED;
            case DOUBTFUL -> ChatFormatting.GOLD;
            case UNKNOWN -> ChatFormatting.GRAY;
        };
    }
}
