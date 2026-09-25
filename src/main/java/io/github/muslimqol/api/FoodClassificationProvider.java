package io.github.muslimqol.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;

/**
 * Provider interface for contributing food classifications to MuslimQoL.
 * <p>
 * <b>Note:</b> This API is experimental during the MuslimQoL 0.2 development cycle
 * and subject to evolution before 1.0.
 */
@ApiStatus.Experimental
public interface FoodClassificationProvider {

    /**
     * Unique, namespace-safe identifier for this provider.
     */
    ClassificationProviderId id();

    /**
     * Precedence tier for this provider.
     */
    ClassificationPriority priority();

    /**
     * Classifies an item by its registry ID and optional ItemStack context.
     *
     * @param itemId ResourceLocation registry key of the item
     * @param stack ItemStack being classified (may be null or empty)
     * @return Optional FoodClassification if this provider recognizes the item
     */
    Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack);

    /**
     * Convenience method to classify by registry key alone.
     */
    default Optional<FoodClassification> classify(ResourceLocation itemId) {
        return classify(itemId, null);
    }

    /**
     * Returns all candidate classifications this provider can contribute for the given item.
     * Most single-source providers return a singleton list containing {@link #classify}.
     * Multi-pack or aggregate providers (like datapacks) may return multiple rules.
     */
    default List<FoodClassification> classifyAll(ResourceLocation itemId, ItemStack stack) {
        return classify(itemId, stack).map(List::of).orElse(List.of());
    }

    /**
     * Convenience method to classify all candidates by registry key alone.
     */
    default List<FoodClassification> classifyAll(ResourceLocation itemId) {
        return classifyAll(itemId, null);
    }

    /**
     * Convenience method to classify by ItemStack.
     */
    default Optional<FoodClassification> classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return classify(id, stack);
    }
}
