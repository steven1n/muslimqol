package io.github.muslimqol;

import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.ConsumptionPolicy;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.api.PigPolicy;
import io.github.muslimqol.food.FoodClassifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PolicyResolutionTest {

    @Test
    void testDefaultPolicyMapping() {
        assertEquals(ConsumptionPolicy.ALLOW, FoodClassifier.getPolicy(FoodStatus.HALAL));
        assertEquals(ConsumptionPolicy.BLOCK, FoodClassifier.getPolicy(FoodStatus.RESTRICTED));
        assertEquals(ConsumptionPolicy.WARN, FoodClassifier.getPolicy(FoodStatus.DOUBTFUL));
        assertEquals(ConsumptionPolicy.ALLOW, FoodClassifier.getPolicy(FoodStatus.UNKNOWN));
        assertEquals(ConsumptionPolicy.ALLOW, FoodClassifier.getPolicy(null));
    }

    @Test
    void testFoodClassificationRecord() {
        FoodClassification classification = new FoodClassification(
                FoodStatus.HALAL,
                "plant_based",
                ClassificationSource.BUILTIN
        );

        assertEquals(FoodStatus.HALAL, classification.status());
        assertEquals("plant_based", classification.reason());
        assertEquals(ClassificationSource.BUILTIN, classification.source());
        assertEquals("food_reason.muslimqol.plant_based", classification.getReasonTranslationKey());

        FoodClassification customKey = new FoodClassification(
                FoodStatus.RESTRICTED,
                "food_reason.custom.reason",
                ClassificationSource.USER_OVERRIDE
        );
        assertEquals("food_reason.custom.reason", customKey.getReasonTranslationKey());

        FoodClassification nullReason = new FoodClassification(
                FoodStatus.UNKNOWN,
                null,
                ClassificationSource.BUILTIN
        );
        assertEquals("unclassified", nullReason.reason());
        assertEquals("food_reason.muslimqol.unclassified", nullReason.getReasonTranslationKey());
    }

    @Test
    void testPigPolicyValues() {
        assertNotNull(PigPolicy.NORMAL);
        assertNotNull(PigPolicy.NO_NATURAL_SPAWN);
        assertNotNull(PigPolicy.NO_PORK_DROPS);
        assertNotNull(PigPolicy.DISABLED_GAMEPLAY);
        assertEquals(4, PigPolicy.values().length);
    }
}
