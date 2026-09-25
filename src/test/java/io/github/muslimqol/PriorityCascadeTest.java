package io.github.muslimqol;

import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.food.FoodClassificationRegistry;
import io.github.muslimqol.food.FoodClassifier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriorityCascadeTest {

    private final ResourceLocation testItem = ResourceLocation.parse("muslimqol:test_food_item");
    private final ResourceLocation vanillaApple = ResourceLocation.parse("minecraft:apple");

    @BeforeAll
    static void init() {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    @BeforeEach
    @AfterEach
    void cleanup() {
        FoodClassificationRegistry.clearAll();
    }

    @Test
    void testLevel5UnknownFallback() {
        // Unknown item with no tag, no datapack, no builtin, no override
        FoodClassification result = FoodClassifier.classify(testItem);
        assertEquals(FoodStatus.UNKNOWN, result.status());
        assertEquals("unclassified", result.reason());
        assertEquals(ClassificationSource.BUILTIN, result.source());
    }

    @Test
    void testLevel4BuiltinPrecedenceOverUnknown() {
        // Built-in item with no overrides
        FoodClassification result = FoodClassifier.classify(vanillaApple);
        assertEquals(FoodStatus.HALAL, result.status());
        assertEquals("plant_based", result.reason());
        assertEquals(ClassificationSource.BUILTIN, result.source());
    }

    @Test
    void testLevel2DatapackPrecedenceOverBuiltin() {
        // Register datapack entry overriding vanilla builtin
        FoodClassificationRegistry.registerDatapackEntry(
                vanillaApple,
                new FoodClassification(FoodStatus.DOUBTFUL, "datapack_concern", ClassificationSource.DATAPACK)
        );

        FoodClassification result = FoodClassifier.classify(vanillaApple);
        assertEquals(FoodStatus.DOUBTFUL, result.status());
        assertEquals("datapack_concern", result.reason());
        assertEquals(ClassificationSource.DATAPACK, result.source());
    }

    @Test
    void testLevel1UserOverridePrecedenceOverDatapackAndBuiltin() {
        // Datapack sets DOUBTFUL
        FoodClassificationRegistry.registerDatapackEntry(
                vanillaApple,
                new FoodClassification(FoodStatus.DOUBTFUL, "datapack_concern", ClassificationSource.DATAPACK)
        );

        // User override sets RESTRICTED (e.g. personal medical or dietary preference)
        FoodClassificationRegistry.registerUserOverride(
                vanillaApple,
                new FoodClassification(FoodStatus.RESTRICTED, "user_custom", ClassificationSource.USER_OVERRIDE)
        );

        FoodClassification result = FoodClassifier.classify(vanillaApple);
        assertEquals(FoodStatus.RESTRICTED, result.status());
        assertEquals("user_custom", result.reason());
        assertEquals(ClassificationSource.USER_OVERRIDE, result.source());

        // Removing user override falls back to DATAPACK
        FoodClassificationRegistry.clearUserOverrides();
        FoodClassification afterUserCleared = FoodClassifier.classify(vanillaApple);
        assertEquals(FoodStatus.DOUBTFUL, afterUserCleared.status());
        assertEquals(ClassificationSource.DATAPACK, afterUserCleared.source());

        // Removing datapack falls back to BUILTIN
        FoodClassificationRegistry.clearDatapack();
        FoodClassification afterDatapackCleared = FoodClassifier.classify(vanillaApple);
        assertEquals(FoodStatus.HALAL, afterDatapackCleared.status());
        assertEquals(ClassificationSource.BUILTIN, afterDatapackCleared.source());
    }
}
