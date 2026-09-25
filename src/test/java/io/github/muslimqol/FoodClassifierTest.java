package io.github.muslimqol;

import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.food.BuiltinFoodData;
import io.github.muslimqol.food.FoodClassificationRegistry;
import io.github.muslimqol.food.FoodClassifier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodClassifierTest {

    @org.junit.jupiter.api.BeforeAll
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
    void testBuiltinClassifications() {
        // Plant-based food should be HALAL
        ResourceLocation apple = ResourceLocation.parse("minecraft:apple");
        FoodClassification appleClass = FoodClassifier.classify(apple);
        assertEquals(FoodStatus.HALAL, appleClass.status());
        assertEquals(ClassificationSource.BUILTIN, appleClass.source());

        // Pork products should be RESTRICTED
        ResourceLocation porkchop = ResourceLocation.parse("minecraft:porkchop");
        FoodClassification porkClass = FoodClassifier.classify(porkchop);
        assertEquals(FoodStatus.RESTRICTED, porkClass.status());
        assertEquals(ClassificationSource.BUILTIN, porkClass.source());

        ResourceLocation cookedPork = ResourceLocation.parse("minecraft:cooked_porkchop");
        FoodClassification cookedPorkClass = FoodClassifier.classify(cookedPork);
        assertEquals(FoodStatus.RESTRICTED, cookedPorkClass.status());

        // Unspecified meats must be UNKNOWN by default
        ResourceLocation beef = ResourceLocation.parse("minecraft:beef");
        FoodClassification beefClass = FoodClassifier.classify(beef);
        assertEquals(FoodStatus.UNKNOWN, beefClass.status());

        ResourceLocation chicken = ResourceLocation.parse("minecraft:chicken");
        FoodClassification chickenClass = FoodClassifier.classify(chicken);
        assertEquals(FoodStatus.UNKNOWN, chickenClass.status());

        ResourceLocation mutton = ResourceLocation.parse("minecraft:mutton");
        FoodClassification muttonClass = FoodClassifier.classify(mutton);
        assertEquals(FoodStatus.UNKNOWN, muttonClass.status());
    }

    @Test
    void testUnknownItemFallback() {
        ResourceLocation unknownModFood = ResourceLocation.parse("somemod:alien_fruit");
        FoodClassification result = FoodClassifier.classify(unknownModFood);

        assertNotNull(result);
        assertEquals(FoodStatus.UNKNOWN, result.status());
        assertEquals("unclassified", result.reason());
        assertEquals("food_reason.muslimqol.unclassified", result.getReasonTranslationKey());
    }

    @Test
    void testDatapackPrecedenceOverBuiltin() {
        ResourceLocation beef = ResourceLocation.parse("minecraft:beef");

        // Initially UNKNOWN
        assertEquals(FoodStatus.UNKNOWN, FoodClassifier.classify(beef).status());

        // Datapack defines it as HALAL (e.g. certified slaughterhouse compatibility pack)
        FoodClassificationRegistry.registerDatapackEntry(
                beef,
                new FoodClassification(FoodStatus.HALAL, "datapack_certified", ClassificationSource.DATAPACK)
        );

        FoodClassification classified = FoodClassifier.classify(beef);
        assertEquals(FoodStatus.HALAL, classified.status());
        assertEquals(ClassificationSource.DATAPACK, classified.source());
        assertEquals("datapack_certified", classified.reason());
    }

    @Test
    void testUserOverridePrecedenceOverAll() {
        ResourceLocation apple = ResourceLocation.parse("minecraft:apple");

        // Built-in is HALAL
        assertEquals(FoodStatus.HALAL, FoodClassifier.classify(apple).status());

        // Datapack sets it to DOUBTFUL
        FoodClassificationRegistry.registerDatapackEntry(
                apple,
                new FoodClassification(FoodStatus.DOUBTFUL, "datapack_doubt", ClassificationSource.DATAPACK)
        );
        assertEquals(FoodStatus.DOUBTFUL, FoodClassifier.classify(apple).status());

        // User override takes highest precedence (e.g. player personal diet restriction)
        FoodClassificationRegistry.registerUserOverride(
                apple,
                new FoodClassification(FoodStatus.RESTRICTED, "custom_allergy", ClassificationSource.USER_OVERRIDE)
        );

        FoodClassification finalResult = FoodClassifier.classify(apple);
        assertEquals(FoodStatus.RESTRICTED, finalResult.status());
        assertEquals(ClassificationSource.USER_OVERRIDE, finalResult.source());
        assertEquals("custom_allergy", finalResult.reason());
    }

    @Test
    void testBuiltinDataCompleteness() {
        Optional<FoodClassification> carrot = BuiltinFoodData.getClassification(ResourceLocation.parse("minecraft:carrot"));
        assertTrue(carrot.isPresent());
        assertEquals(FoodStatus.HALAL, carrot.get().status());

        Optional<FoodClassification> rottenFlesh = BuiltinFoodData.getClassification(ResourceLocation.parse("minecraft:rotten_flesh"));
        assertTrue(rottenFlesh.isPresent());
        assertEquals(FoodStatus.RESTRICTED, rottenFlesh.get().status());

        Optional<FoodClassification> suspiciousStew = BuiltinFoodData.getClassification(ResourceLocation.parse("minecraft:suspicious_stew"));
        assertTrue(suspiciousStew.isPresent());
        assertEquals(FoodStatus.DOUBTFUL, suspiciousStew.get().status());
    }
}
