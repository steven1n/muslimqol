package io.github.muslimqol.food;

import io.github.muslimqol.api.ClassificationResolution;
import io.github.muslimqol.api.ConsumptionPolicy;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.compat.FoodCompatibilityManager;
import io.github.muslimqol.config.CommonConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Core food classification engine enforcing the priority hierarchy:
 * USER_OVERRIDE -> DATAPACK -> ITEM_TAG -> BUILTIN -> UNKNOWN.
 * <p>
 * Delegates evaluation to {@link FoodCompatibilityManager} for unified resolution and conflict diagnostics.
 */
public final class FoodClassifier {

    private FoodClassifier() {}

    /**
     * Determines if an ItemStack represents edible food or is explicitly classified.
     */
    public static boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.has(DataComponents.FOOD)) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return isKnownOrClassified(id, stack);
    }

    /**
     * Determines if an Item is food or explicitly classified.
     */
    public static boolean isFood(Item item) {
        if (item == null) {
            return false;
        }
        if (item.components().has(DataComponents.FOOD)) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return isKnownOrClassified(id, null);
    }

    private static boolean isKnownOrClassified(ResourceLocation id, ItemStack stack) {
        if (id == null) return false;
        FoodClassification classification = FoodCompatibilityManager.classify(id, stack);
        return classification.status() != FoodStatus.UNKNOWN;
    }

    /**
     * Classifies an ItemStack based on the priority chain.
     */
    public static FoodClassification classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return FoodClassification.unknown();
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return classify(itemId, stack);
    }

    /**
     * Classifies an Item based on the priority chain.
     */
    public static FoodClassification classify(Item item) {
        if (item == null) {
            return FoodClassification.unknown();
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        return classify(itemId, null);
    }

    /**
     * Classifies a ResourceLocation item identifier.
     */
    public static FoodClassification classify(ResourceLocation itemId) {
        return classify(itemId, null);
    }

    /**
     * Resolves classification for an item identifier and optional stack through the compatibility manager.
     */
    public static FoodClassification classify(ResourceLocation itemId, ItemStack stack) {
        if (itemId == null) {
            return FoodClassification.unknown();
        }
        return FoodCompatibilityManager.classify(itemId, stack);
    }

    /**
     * Resolves classification with full provenance and conflict diagnostics.
     */
    public static ClassificationResolution resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ClassificationResolution.ofSingle(FoodClassification.unknown());
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return resolve(itemId, stack);
    }

    /**
     * Resolves classification with full provenance and conflict diagnostics for an Item.
     */
    public static ClassificationResolution resolve(Item item) {
        if (item == null) {
            return ClassificationResolution.ofSingle(FoodClassification.unknown());
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        return resolve(itemId, null);
    }

    /**
     * Resolves classification with full provenance and conflict diagnostics for a ResourceLocation.
     */
    public static ClassificationResolution resolve(ResourceLocation itemId) {
        return resolve(itemId, null);
    }

    /**
     * Full diagnostic resolution through the compatibility engine.
     */
    public static ClassificationResolution resolve(ResourceLocation itemId, ItemStack stack) {
        if (itemId == null) {
            return ClassificationResolution.ofSingle(FoodClassification.unknown());
        }
        return FoodCompatibilityManager.resolve(itemId, stack);
    }

    /**
     * Resolves the effective consumption policy for a given FoodStatus.
     */
    public static ConsumptionPolicy getPolicy(FoodStatus status) {
        if (status == null) {
            return ConsumptionPolicy.ALLOW;
        }

        if (CommonConfig.SPEC != null && CommonConfig.SPEC.isLoaded()) {
            return switch (status) {
                case HALAL -> CommonConfig.HALAL_POLICY.get();
                case RESTRICTED -> CommonConfig.RESTRICTED_POLICY.get();
                case DOUBTFUL -> CommonConfig.DOUBTFUL_POLICY.get();
                case UNKNOWN -> CommonConfig.UNKNOWN_POLICY.get();
            };
        }

        // Default policies when configuration is not loaded
        return switch (status) {
            case HALAL -> ConsumptionPolicy.ALLOW;
            case RESTRICTED -> ConsumptionPolicy.BLOCK;
            case DOUBTFUL -> ConsumptionPolicy.WARN;
            case UNKNOWN -> ConsumptionPolicy.ALLOW;
        };
    }
}
