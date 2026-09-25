package io.github.muslimqol.food;

import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in baseline classification dataset for vanilla Minecraft foods.
 */
public final class BuiltinFoodData {

    private static final Map<ResourceLocation, FoodClassification> ENTRIES;

    static {
        Map<ResourceLocation, FoodClassification> map = new HashMap<>();

        // Plant-based foods (HALAL)
        register(map, "minecraft:apple", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:golden_apple", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:enchanted_golden_apple", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:carrot", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:golden_carrot", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:potato", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:baked_potato", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:bread", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:melon_slice", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:sweet_berries", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:glow_berries", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:chorus_fruit", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:beetroot", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:beetroot_soup", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:dried_kelp", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:cookie", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:pumpkin_pie", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:mushroom_stew", FoodStatus.HALAL, "plant_based");
        register(map, "minecraft:honey_bottle", FoodStatus.HALAL, "honey");

        // Seafood (HALAL)
        register(map, "minecraft:cod", FoodStatus.HALAL, "fish");
        register(map, "minecraft:cooked_cod", FoodStatus.HALAL, "fish");
        register(map, "minecraft:salmon", FoodStatus.HALAL, "fish");
        register(map, "minecraft:cooked_salmon", FoodStatus.HALAL, "fish");
        register(map, "minecraft:tropical_fish", FoodStatus.HALAL, "fish");

        // Swine and harmful foods (RESTRICTED)
        register(map, "minecraft:porkchop", FoodStatus.RESTRICTED, "swine");
        register(map, "minecraft:cooked_porkchop", FoodStatus.RESTRICTED, "swine");
        register(map, "minecraft:rotten_flesh", FoodStatus.RESTRICTED, "carrion");
        register(map, "minecraft:pufferfish", FoodStatus.RESTRICTED, "poisonous");
        register(map, "minecraft:spider_eye", FoodStatus.RESTRICTED, "poisonous");

        // Doubtful foods (DOUBTFUL)
        register(map, "minecraft:poisonous_potato", FoodStatus.DOUBTFUL, "toxic_plant");
        register(map, "minecraft:suspicious_stew", FoodStatus.DOUBTFUL, "unknown_ingredients");

        // Meats requiring explicit configuration (UNKNOWN)
        register(map, "minecraft:beef", FoodStatus.UNKNOWN, "unspecified_meat");
        register(map, "minecraft:cooked_beef", FoodStatus.UNKNOWN, "unspecified_meat");
        register(map, "minecraft:chicken", FoodStatus.UNKNOWN, "unspecified_meat");
        register(map, "minecraft:cooked_chicken", FoodStatus.UNKNOWN, "unspecified_meat");
        register(map, "minecraft:mutton", FoodStatus.UNKNOWN, "unspecified_meat");
        register(map, "minecraft:cooked_mutton", FoodStatus.UNKNOWN, "unspecified_meat");
        register(map, "minecraft:rabbit", FoodStatus.UNKNOWN, "unspecified_meat");
        register(map, "minecraft:cooked_rabbit", FoodStatus.UNKNOWN, "unspecified_meat");
        register(map, "minecraft:rabbit_stew", FoodStatus.UNKNOWN, "unspecified_meat");

        ENTRIES = Collections.unmodifiableMap(map);
    }

    private static void register(Map<ResourceLocation, FoodClassification> map, String id, FoodStatus status, String reason) {
        map.put(ResourceLocation.parse(id), new FoodClassification(status, reason, ClassificationSource.BUILTIN));
    }

    public static Optional<FoodClassification> getClassification(ResourceLocation id) {
        return Optional.ofNullable(ENTRIES.get(id));
    }

    public static Map<ResourceLocation, FoodClassification> getAllEntries() {
        return ENTRIES;
    }

    private BuiltinFoodData() {}
}
