package io.github.muslimqol.food;

import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.ConsumptionPolicy;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.config.CommonConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Core food classification engine enforcing the priority hierarchy:
 * USER_OVERRIDE -> DATAPACK -> ITEM_TAG -> BUILTIN -> UNKNOWN.
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
        if (FoodClassificationRegistry.getUserOverride(id).isPresent()) return true;
        if (FoodClassificationRegistry.getDatapackClassification(id).isPresent()) return true;
        if (stack != null && FoodTagResolver.resolveTag(stack).isPresent()) return true;
        return BuiltinFoodData.getClassification(id).isPresent();
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
     * Internal classification resolver following strict priority:
     * 1. USER_OVERRIDE
     * 2. DATAPACK
     * 3. ITEM_TAG
     * 4. BUILTIN
     * 5. UNKNOWN
     */
    public static FoodClassification classify(ResourceLocation itemId, ItemStack stack) {
        if (itemId == null) {
            return FoodClassification.unknown();
        }

        // 1. User Override
        Optional<FoodClassification> userOverride = FoodClassificationRegistry.getUserOverride(itemId);
        if (userOverride.isPresent()) {
            return userOverride.get();
        }

        // 2. Datapack
        Optional<FoodClassification> datapack = FoodClassificationRegistry.getDatapackClassification(itemId);
        if (datapack.isPresent()) {
            return datapack.get();
        }

        // 3. Item Tag
        Optional<FoodClassification> tagResult = stack != null
                ? FoodTagResolver.resolveTag(stack)
                : FoodTagResolver.resolveTag(itemId);
        if (tagResult.isPresent()) {
            return tagResult.get();
        }

        // 4. Built-in
        Optional<FoodClassification> builtin = BuiltinFoodData.getClassification(itemId);
        if (builtin.isPresent()) {
            return builtin.get();
        }

        // 5. Unknown Fallback
        return new FoodClassification(FoodStatus.UNKNOWN, "unclassified", ClassificationSource.BUILTIN);
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
