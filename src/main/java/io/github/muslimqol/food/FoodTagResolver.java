package io.github.muslimqol.food;

import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Resolves item food classification against datapack item tags.
 */
public final class FoodTagResolver {

    public static final TagKey<Item> HALAL_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("muslimqol", "food/halal")
    );

    public static final TagKey<Item> RESTRICTED_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("muslimqol", "food/restricted")
    );

    public static final TagKey<Item> DOUBTFUL_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("muslimqol", "food/doubtful")
    );

    public static Optional<FoodClassification> resolveTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        if (stack.is(RESTRICTED_TAG)) {
            return Optional.of(new FoodClassification(FoodStatus.RESTRICTED, "tag_restricted", ClassificationSource.ITEM_TAG));
        }
        if (stack.is(DOUBTFUL_TAG)) {
            return Optional.of(new FoodClassification(FoodStatus.DOUBTFUL, "tag_doubtful", ClassificationSource.ITEM_TAG));
        }
        if (stack.is(HALAL_TAG)) {
            return Optional.of(new FoodClassification(FoodStatus.HALAL, "tag_halal", ClassificationSource.ITEM_TAG));
        }
        return Optional.empty();
    }

    public static Optional<FoodClassification> resolveTag(Item item) {
        if (item == null) {
            return Optional.empty();
        }
        try {
            var holder = item.builtInRegistryHolder();
            if (holder.is(RESTRICTED_TAG)) {
                return Optional.of(new FoodClassification(FoodStatus.RESTRICTED, "tag_restricted", ClassificationSource.ITEM_TAG));
            }
            if (holder.is(DOUBTFUL_TAG)) {
                return Optional.of(new FoodClassification(FoodStatus.DOUBTFUL, "tag_doubtful", ClassificationSource.ITEM_TAG));
            }
            if (holder.is(HALAL_TAG)) {
                return Optional.of(new FoodClassification(FoodStatus.HALAL, "tag_halal", ClassificationSource.ITEM_TAG));
            }
        } catch (Throwable ignored) {
            // Registry holder not bound or uninitialized
        }
        return Optional.empty();
    }

    public static Optional<FoodClassification> resolveTag(ResourceLocation itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        try {
            Item item = BuiltInRegistries.ITEM.get(itemId);
            return resolveTag(item);
        } catch (Throwable ignored) {
            // Registry uninitialized or not bootstrapped
            return Optional.empty();
        }
    }

    private FoodTagResolver() {}
}
