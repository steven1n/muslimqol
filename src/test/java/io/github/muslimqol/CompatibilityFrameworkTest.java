package io.github.muslimqol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.muslimqol.api.ClassificationPriority;
import io.github.muslimqol.api.ClassificationProviderId;
import io.github.muslimqol.api.ClassificationResolution;
import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodClassificationProvider;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.compat.CompatibilityMetadata;
import io.github.muslimqol.compat.FoodCompatibilityManager;
import io.github.muslimqol.data.FoodClassificationJsonLoader;
import io.github.muslimqol.food.FoodClassificationRegistry;
import io.github.muslimqol.food.FoodClassifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityFrameworkTest {

    private final ResourceLocation appleId = ResourceLocation.parse("minecraft:apple");
    private final ResourceLocation porkId = ResourceLocation.parse("minecraft:porkchop");
    private final ResourceLocation unknownId = ResourceLocation.parse("examplemod:fictional_berry");

    @BeforeAll
    static void init() {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    @BeforeEach
    @AfterEach
    void resetState() {
        FoodClassificationRegistry.clearAll();
        FoodCompatibilityManager.resetToDefaults();
        FoodCompatibilityManager.setModLoadedChecker(null);
    }

    @Test
    void testBuiltinOnlyResolution() {
        ClassificationResolution resolution = FoodClassifier.resolve(appleId);
        assertNotNull(resolution);
        assertEquals(FoodStatus.HALAL, resolution.selected().status());
        assertEquals("plant_based", resolution.selected().reason());
        assertEquals(ClassificationSource.BUILTIN, resolution.selected().source());
        assertEquals(ClassificationProviderId.BUILTIN, resolution.selected().providerId());
        assertEquals(ClassificationPriority.BUILTIN, resolution.selected().priority());
        assertFalse(resolution.conflicted());
        assertEquals(1, resolution.candidates().size());
    }

    @Test
    void testUnknownFallbackResolution() {
        ClassificationResolution resolution = FoodClassifier.resolve(unknownId);
        assertNotNull(resolution);
        assertEquals(FoodStatus.UNKNOWN, resolution.selected().status());
        assertEquals("unclassified", resolution.selected().reason());
        assertEquals(ClassificationPriority.UNKNOWN, resolution.selected().priority());
        assertFalse(resolution.conflicted());
        assertTrue(resolution.candidates().isEmpty());
    }

    @Test
    void testDatapackOverridesBuiltin() {
        FoodClassificationRegistry.registerDatapackEntry(
                appleId,
                new FoodClassification(
                        FoodStatus.DOUBTFUL,
                        "datapack_concern",
                        ClassificationSource.DATAPACK,
                        ClassificationProviderId.parse("custompack:apples"),
                        ClassificationPriority.DATAPACK
                )
        );

        ClassificationResolution resolution = FoodClassifier.resolve(appleId);
        assertEquals(FoodStatus.DOUBTFUL, resolution.selected().status());
        assertEquals(ClassificationPriority.DATAPACK, resolution.selected().priority());
        assertEquals("custompack:apples", resolution.selected().providerId().toString());
        assertFalse(resolution.conflicted());
        assertTrue(resolution.candidates().size() >= 2);
    }

    @Test
    void testUserOverrideOverridesDatapackAndBuiltin() {
        FoodClassificationRegistry.registerDatapackEntry(
                porkId,
                new FoodClassification(FoodStatus.HALAL, "datapack_mod", ClassificationSource.DATAPACK)
        );
        FoodClassificationRegistry.registerUserOverride(
                porkId,
                new FoodClassification(FoodStatus.RESTRICTED, "user_strict", ClassificationSource.USER_OVERRIDE)
        );

        ClassificationResolution resolution = FoodClassifier.resolve(porkId);
        assertEquals(FoodStatus.RESTRICTED, resolution.selected().status());
        assertEquals(ClassificationPriority.USER_OVERRIDE, resolution.selected().priority());
        assertEquals("user_strict", resolution.selected().reason());
        assertFalse(resolution.conflicted());
    }

    @Test
    void testSamePriorityIdenticalClassificationsNoConflict() {
        ClassificationProviderId providerA = ClassificationProviderId.parse("provider_a:fruits");
        ClassificationProviderId providerB = ClassificationProviderId.parse("provider_b:orchard");

        FoodCompatibilityManager.registerProvider(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return providerA; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.DATAPACK; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                if (itemId.equals(unknownId)) {
                    return Optional.of(new FoodClassification(FoodStatus.HALAL, "natural_fruit", ClassificationSource.DATAPACK, providerA, ClassificationPriority.DATAPACK));
                }
                return Optional.empty();
            }
        });

        FoodCompatibilityManager.registerProvider(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return providerB; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.DATAPACK; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                if (itemId.equals(unknownId)) {
                    return Optional.of(new FoodClassification(FoodStatus.HALAL, "organic_fruit", ClassificationSource.DATAPACK, providerB, ClassificationPriority.DATAPACK));
                }
                return Optional.empty();
            }
        });

        ClassificationResolution resolution = FoodClassifier.resolve(unknownId);
        assertEquals(FoodStatus.HALAL, resolution.selected().status());
        assertFalse(resolution.conflicted(), "Identical statuses at same priority must not flag conflict");
        assertEquals(2, resolution.candidates().size());
        assertEquals(providerA, resolution.selected().providerId());
    }

    @Test
    void testSamePriorityConflictingClassificationsFlagsConflict() {
        ClassificationProviderId providerA = ClassificationProviderId.parse("provider_a:rules");
        ClassificationProviderId providerB = ClassificationProviderId.parse("provider_b:rules");

        FoodCompatibilityManager.registerProvider(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return providerA; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.DATAPACK; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                if (itemId.equals(unknownId)) {
                    return Optional.of(new FoodClassification(FoodStatus.HALAL, "provider_a_permissive", ClassificationSource.DATAPACK, providerA, ClassificationPriority.DATAPACK));
                }
                return Optional.empty();
            }
        });

        FoodCompatibilityManager.registerProvider(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return providerB; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.DATAPACK; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                if (itemId.equals(unknownId)) {
                    return Optional.of(new FoodClassification(FoodStatus.RESTRICTED, "provider_b_strict", ClassificationSource.DATAPACK, providerB, ClassificationPriority.DATAPACK));
                }
                return Optional.empty();
            }
        });

        ClassificationResolution resolution = FoodClassifier.resolve(unknownId);
        assertTrue(resolution.conflicted(), "Different statuses at same highest priority must flag conflict");
        assertEquals(providerA, resolution.selected().providerId(), "Provider A must win lexicographically");
        assertEquals(FoodStatus.HALAL, resolution.selected().status());
        assertEquals(2, resolution.candidates().size());
    }

    @Test
    void testDeterministicTieResolutionIndependentOfRegistrationOrder() {
        ClassificationProviderId packZ = ClassificationProviderId.parse("pack_z:rules");
        ClassificationProviderId packA = ClassificationProviderId.parse("pack_a:rules");

        FoodClassificationProvider providerZ = new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return packZ; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.DATAPACK; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                return itemId.equals(unknownId)
                        ? Optional.of(new FoodClassification(FoodStatus.RESTRICTED, "strict_z", ClassificationSource.DATAPACK, packZ, ClassificationPriority.DATAPACK))
                        : Optional.empty();
            }
        };

        FoodClassificationProvider providerA = new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return packA; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.DATAPACK; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                return itemId.equals(unknownId)
                        ? Optional.of(new FoodClassification(FoodStatus.HALAL, "halal_a", ClassificationSource.DATAPACK, packA, ClassificationPriority.DATAPACK))
                        : Optional.empty();
            }
        };

        // Register Z then A
        FoodCompatibilityManager.registerProvider(providerZ);
        FoodCompatibilityManager.registerProvider(providerA);
        ClassificationResolution res1 = FoodClassifier.resolve(unknownId);

        // Reset and register A then Z
        FoodCompatibilityManager.resetToDefaults();
        FoodCompatibilityManager.registerProvider(providerA);
        FoodCompatibilityManager.registerProvider(providerZ);
        ClassificationResolution res2 = FoodClassifier.resolve(unknownId);

        assertEquals(res1.selected().providerId(), res2.selected().providerId());
        assertEquals(packA, res1.selected().providerId(), "pack_a must win tie-breaker over pack_z lexicographically");
        assertEquals(FoodStatus.HALAL, res1.selected().status());
        assertEquals(FoodStatus.HALAL, res2.selected().status());
    }

    @Test
    void testProviderRegistrationAndUnregistration() {
        ClassificationProviderId customId = ClassificationProviderId.parse("custom_addon:foods");
        FoodClassificationProvider customProvider = new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return customId; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.DATAPACK; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                if (itemId.equals(unknownId)) {
                    return Optional.of(new FoodClassification(FoodStatus.HALAL, "custom_addon", ClassificationSource.DATAPACK, customId, ClassificationPriority.DATAPACK));
                }
                return Optional.empty();
            }
        };

        assertEquals(FoodStatus.UNKNOWN, FoodClassifier.classify(unknownId).status());

        FoodCompatibilityManager.registerProvider(customProvider);
        assertEquals(FoodStatus.HALAL, FoodClassifier.classify(unknownId).status());

        boolean removed = FoodCompatibilityManager.unregisterProvider(customId);
        assertTrue(removed);
        assertEquals(FoodStatus.UNKNOWN, FoodClassifier.classify(unknownId).status());
    }

    @Test
    void testMissingTargetModMetadataSkipped() {
        JsonObject entryJson = JsonParser.parseString("""
                {
                  "status": "HALAL",
                  "reason": "compat_mod_food"
                }
                """).getAsJsonObject();

        ResourceLocation fileId = ResourceLocation.parse("farmersdelight:food_classifications/tomato");
        CompatibilityMetadata meta = new CompatibilityMetadata(1, "Farmer's Delight Compat", "farmersdelight");

        // Simulate farmersdelight is NOT loaded
        Map<String, CompatibilityMetadata> active = Map.of();
        Map<String, CompatibilityMetadata> skipped = Map.of("farmersdelight", meta);

        var result = FoodClassificationJsonLoader.parseAll(
                Map.of(fileId, entryJson),
                active,
                skipped
        );

        assertTrue(result.isEmpty(), "Entries from skipped compatibility pack must not be loaded");
    }

    @Test
    void testPresentTargetModMetadataLoaded() {
        JsonObject entryJson = JsonParser.parseString("""
                {
                  "status": "HALAL",
                  "reason": "compat_mod_food"
                }
                """).getAsJsonObject();

        ResourceLocation fileId = ResourceLocation.parse("farmersdelight:food_classifications/tomato");
        CompatibilityMetadata meta = new CompatibilityMetadata(1, "Farmer's Delight Compat", "farmersdelight");

        Map<String, CompatibilityMetadata> active = Map.of("farmersdelight", meta);
        Map<String, CompatibilityMetadata> skipped = Map.of();

        var result = FoodClassificationJsonLoader.parseAll(
                Map.of(fileId, entryJson),
                active,
                skipped
        );

        assertEquals(1, result.size());
        FoodClassification classification = result.get(fileId);
        assertNotNull(classification);
        assertEquals(FoodStatus.HALAL, classification.status());
        assertEquals("farmersdelight:datapack", classification.providerId().toString());
    }

    @Test
    void testLegacyDatapackCompatibilityWithoutMetadata() {
        JsonObject entryJson = JsonParser.parseString("""
                {
                  "status": "RESTRICTED",
                  "reason": "legacy_custom"
                }
                """).getAsJsonObject();

        ResourceLocation fileId = ResourceLocation.parse("legacy_pack:food_classifications/bacon");

        var result = FoodClassificationJsonLoader.parseAll(Map.of(fileId, entryJson));
        assertEquals(1, result.size());
        assertEquals(FoodStatus.RESTRICTED, result.get(fileId).status());
        assertEquals("legacy_custom", result.get(fileId).reason());
        assertEquals(ClassificationSource.DATAPACK, result.get(fileId).source());
    }

    @Test
    void testProviderExceptionRecovery() {
        ClassificationProviderId brokenId = ClassificationProviderId.parse("broken_mod:broken_provider");

        FoodCompatibilityManager.registerProvider(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return brokenId; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.USER_OVERRIDE; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                throw new RuntimeException("Simulated provider failure");
            }
        });

        // Despite broken provider throwing, resolution must catch it safely and fallback to builtin apple
        ClassificationResolution resolution = FoodClassifier.resolve(appleId);
        assertNotNull(resolution);
        assertEquals(FoodStatus.HALAL, resolution.selected().status());
        assertEquals(ClassificationProviderId.BUILTIN, resolution.selected().providerId());
    }

    @Test
    void testV01PrecedenceRegression() {
        // Vanilla Apple is BUILTIN HALAL
        assertEquals(FoodStatus.HALAL, FoodClassifier.classify(appleId).status());

        // Datapack overrides BUILTIN
        FoodClassificationRegistry.registerDatapackEntry(
                appleId,
                new FoodClassification(FoodStatus.DOUBTFUL, "doubt", ClassificationSource.DATAPACK)
        );
        assertEquals(FoodStatus.DOUBTFUL, FoodClassifier.classify(appleId).status());

        // User override overrides Datapack
        FoodClassificationRegistry.registerUserOverride(
                appleId,
                new FoodClassification(FoodStatus.RESTRICTED, "blocked", ClassificationSource.USER_OVERRIDE)
        );
        assertEquals(FoodStatus.RESTRICTED, FoodClassifier.classify(appleId).status());

        // Clearing user override restores Datapack
        FoodClassificationRegistry.clearUserOverrides();
        assertEquals(FoodStatus.DOUBTFUL, FoodClassifier.classify(appleId).status());

        // Clearing datapack restores Builtin
        FoodClassificationRegistry.clearDatapack();
        assertEquals(FoodStatus.HALAL, FoodClassifier.classify(appleId).status());
    }
}
