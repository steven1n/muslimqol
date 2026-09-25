package io.github.muslimqol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.muslimqol.api.ClassificationPriority;
import io.github.muslimqol.api.ClassificationProviderId;
import io.github.muslimqol.api.ClassificationResolution;
import io.github.muslimqol.api.ClassificationRuleId;
import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodClassificationProvider;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.compat.ClassificationRuntimeState;
import io.github.muslimqol.compat.CompatibilityMetadata;
import io.github.muslimqol.compat.CompatibilitySnapshot;
import io.github.muslimqol.compat.FoodCompatibilityManager;
import io.github.muslimqol.compat.MetadataParseResult;
import io.github.muslimqol.data.FoodClassificationJsonLoader;
import io.github.muslimqol.food.FoodClassificationRegistry;
import io.github.muslimqol.food.FoodClassifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void testFastPathEquivalenceAcrossTiersAndConflicts() {
        // Tier 1: BUILTIN (apple)
        assertEquals(FoodClassifier.resolve(appleId).selected(), FoodClassifier.classify(appleId));

        // Tier 2: UNKNOWN (unknownId)
        assertEquals(FoodClassifier.resolve(unknownId).selected(), FoodClassifier.classify(unknownId));

        // Tier 3: DATAPACK single
        ResourceLocation berryId = ResourceLocation.parse("examplemod:berry");
        FoodClassificationRegistry.registerDatapackEntry(berryId, new FoodClassification(FoodStatus.HALAL, "berry_datapack", ClassificationSource.DATAPACK));
        assertEquals(FoodClassifier.resolve(berryId).selected(), FoodClassifier.classify(berryId));

        // Tier 4: DATAPACK conflict
        ResourceLocation contestedId = ResourceLocation.parse("examplemod:contested");
        FoodClassificationRegistry.registerDatapackEntry(contestedId, new FoodClassification(FoodStatus.HALAL, "contested_a", ClassificationSource.DATAPACK, ClassificationProviderId.parse("pack_a:datapack"), ClassificationPriority.DATAPACK));
        FoodClassificationRegistry.registerDatapackEntry(contestedId, new FoodClassification(FoodStatus.RESTRICTED, "contested_b", ClassificationSource.DATAPACK, ClassificationProviderId.parse("pack_b:datapack"), ClassificationPriority.DATAPACK));
        assertTrue(FoodClassifier.resolve(contestedId).conflicted());
        assertEquals(FoodClassifier.resolve(contestedId).selected(), FoodClassifier.classify(contestedId));

        // Tier 5: USER_OVERRIDE
        ResourceLocation overrideId = ResourceLocation.parse("examplemod:overridden");
        FoodClassificationRegistry.registerUserOverride(overrideId, new FoodClassification(FoodStatus.RESTRICTED, "user_choice", ClassificationSource.USER_OVERRIDE));
        assertEquals(FoodClassifier.resolve(overrideId).selected(), FoodClassifier.classify(overrideId));
    }

    @Test
    void testMalformedProviderIdentityNormalization() {
        ClassificationProviderId registeredId = ClassificationProviderId.parse("valid_mod:provider");
        ClassificationProviderId spoofedId = ClassificationProviderId.parse("spoofed_mod:fake");

        FoodCompatibilityManager.registerProvider(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return registeredId; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.DATAPACK; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                if (itemId.equals(unknownId)) {
                    return Optional.of(new FoodClassification(FoodStatus.HALAL, "reason", ClassificationSource.DATAPACK, spoofedId, ClassificationPriority.DATAPACK));
                }
                return Optional.empty();
            }
        });

        ClassificationResolution resolution = FoodClassifier.resolve(unknownId);
        assertEquals(FoodStatus.HALAL, resolution.selected().status());
        assertEquals(registeredId, resolution.selected().providerId(), "Selected providerId must be normalized to registered provider ID");
        assertEquals(registeredId, resolution.candidates().get(0).providerId(), "Candidate providerId must be normalized to registered provider ID");
        assertEquals(registeredId, resolution.candidates().get(0).classification().providerId(), "Candidate inner classification providerId must be normalized");
        assertEquals(registeredId, FoodClassifier.classify(unknownId).providerId(), "Fast path providerId must be normalized to registered provider ID");
    }

    @Test
    void testElevatedPriorityNormalization() {
        ClassificationProviderId modProviderId = ClassificationProviderId.parse("sneaky_mod:provider");

        FoodCompatibilityManager.registerProvider(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return modProviderId; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.DATAPACK; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                if (itemId.equals(unknownId)) {
                    return Optional.of(new FoodClassification(FoodStatus.HALAL, "sneaky_reason", ClassificationSource.USER_OVERRIDE, modProviderId, ClassificationPriority.USER_OVERRIDE));
                }
                return Optional.empty();
            }
        });

        ClassificationResolution resolution = FoodClassifier.resolve(unknownId);
        assertEquals(FoodStatus.HALAL, resolution.selected().status());
        assertEquals(ClassificationPriority.DATAPACK, resolution.selected().priority());
        assertEquals(ClassificationPriority.DATAPACK, resolution.candidates().get(0).priority());
        assertEquals(ClassificationPriority.DATAPACK, resolution.candidates().get(0).classification().priority());
        assertEquals(ClassificationPriority.DATAPACK, FoodClassifier.classify(unknownId).priority());
    }

    @Test
    void testReservedProviderRegistrationRejection() {
        assertThrows(IllegalArgumentException.class, () -> FoodCompatibilityManager.registerProvider(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return ClassificationProviderId.USER_OVERRIDE; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.USER_OVERRIDE; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) { return Optional.empty(); }
        }));

        assertThrows(IllegalArgumentException.class, () -> FoodCompatibilityManager.registerProvider(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return ClassificationProviderId.DATAPACK; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.DATAPACK; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) { return Optional.empty(); }
        }));

        assertThrows(IllegalArgumentException.class, () -> FoodCompatibilityManager.registerProvider(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return ClassificationProviderId.ITEM_TAG; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.ITEM_TAG; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) { return Optional.empty(); }
        }));

        assertThrows(IllegalArgumentException.class, () -> FoodCompatibilityManager.registerProvider(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return ClassificationProviderId.BUILTIN; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.BUILTIN; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) { return Optional.empty(); }
        }));
    }

    @Test
    void testReservedProviderUnregistrationRejection() {
        assertFalse(FoodCompatibilityManager.unregisterProvider(ClassificationProviderId.USER_OVERRIDE));
        assertFalse(FoodCompatibilityManager.unregisterProvider(ClassificationProviderId.DATAPACK));
        assertFalse(FoodCompatibilityManager.unregisterProvider(ClassificationProviderId.ITEM_TAG));
        assertFalse(FoodCompatibilityManager.unregisterProvider(ClassificationProviderId.BUILTIN));
    }

    @Test
    void testDatapackMultiCandidatePreservation() {
        JsonObject itemEntryA = JsonParser.parseString("""
                {
                  "item": "examplemod:multi_berry",
                  "status": "HALAL",
                  "reason": "halal_variant"
                }
                """).getAsJsonObject();
        JsonObject itemEntryB = JsonParser.parseString("""
                {
                  "item": "examplemod:multi_berry",
                  "status": "RESTRICTED",
                  "reason": "restricted_variant"
                }
                """).getAsJsonObject();

        ResourceLocation commonBerryId = ResourceLocation.parse("examplemod:multi_berry");
        Map<ResourceLocation, List<FoodClassification>> multiParsed = FoodClassificationJsonLoader.parseAllMulti(
                Map.of(
                        ResourceLocation.parse("pack_a:food_classifications/common"), itemEntryA,
                        ResourceLocation.parse("pack_b:food_classifications/common"), itemEntryB
                )
        );

        List<FoodClassification> commonList = multiParsed.get(commonBerryId);
        assertNotNull(commonList);
        assertEquals(2, commonList.size(), "Both datapack candidates must be preserved");

        FoodClassificationRegistry.setDatapackMultiClassifications(multiParsed);

        ClassificationResolution resolution = FoodClassifier.resolve(commonBerryId);
        assertNotNull(resolution);
        assertTrue(resolution.conflicted(), "Different statuses at DATAPACK priority must mark conflicted == true");
        assertEquals(2, resolution.candidates().size(), "Candidates list must contain both datapack candidates");
        assertEquals("pack_a:datapack", resolution.selected().providerId().toString(), "pack_a must win lexicographically over pack_b");
        assertEquals(FoodStatus.HALAL, resolution.selected().status());
    }

    @Test
    void testMetadataFormatValidation() {
        JsonObject validJson = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Valid Pack",
                  "target_mod": "somemod"
                }
                """).getAsJsonObject();
        Optional<CompatibilityMetadata> validMeta = CompatibilityMetadata.fromJson(validJson);
        assertTrue(validMeta.isPresent());
        assertEquals(1, validMeta.get().format());

        JsonObject invalidJson = JsonParser.parseString("""
                {
                  "format": 999,
                  "name": "Future Pack",
                  "target_mod": "somemod"
                }
                """).getAsJsonObject();
        Optional<CompatibilityMetadata> invalidMeta = CompatibilityMetadata.fromJson(invalidJson);
        assertTrue(invalidMeta.isEmpty(), "Unsupported format 999 must be rejected safely");

        JsonObject zeroJson = JsonParser.parseString("""
                {
                  "format": 0,
                  "name": "Zero Pack"
                }
                """).getAsJsonObject();
        assertTrue(CompatibilityMetadata.fromJson(zeroJson).isEmpty(), "Format 0 must be rejected");
    }

    @Test
    void testAtomicDatapackReloadConcurrency() {
        ResourceLocation testId = ResourceLocation.parse("testmod:apple");
        FoodClassification c1 = new FoodClassification(FoodStatus.HALAL, "c1", ClassificationSource.DATAPACK);
        FoodClassification c2 = new FoodClassification(FoodStatus.RESTRICTED, "c2", ClassificationSource.DATAPACK);

        FoodClassificationRegistry.registerDatapackEntry(testId, c1);

        for (int i = 0; i < 100; i++) {
            FoodClassification current = FoodClassifier.classify(testId);
            assertNotNull(current);
            assertTrue(current.status() == FoodStatus.HALAL || current.status() == FoodStatus.RESTRICTED,
                    "Reader must always observe a complete valid state, never empty or partial");

            if (i % 2 == 0) {
                FoodClassificationRegistry.setDatapackMultiClassifications(Map.of(testId, List.of(c2)));
            } else {
                FoodClassificationRegistry.setDatapackMultiClassifications(Map.of(testId, List.of(c1)));
            }
        }
    }

    @Test
    void testClassificationRuleIdContract() {
        ClassificationRuleId ruleA = ClassificationRuleId.of("mypack", "food_classifications/rule_a");
        ClassificationRuleId ruleB = ClassificationRuleId.parse("mypack:food_classifications/rule_b");
        ClassificationRuleId ruleA2 = ClassificationRuleId.parse("mypack:food_classifications/rule_a");

        assertEquals(ruleA, ruleA2);
        assertEquals(ruleA.hashCode(), ruleA2.hashCode());
        assertEquals("mypack:food_classifications/rule_a", ruleA.toString());
        assertEquals(ResourceLocation.parse("mypack:food_classifications/rule_a"), ruleA.id());

        assertTrue(ruleA.compareTo(ruleB) < 0);
        assertTrue(ruleB.compareTo(ruleA) > 0);
        assertEquals(0, ruleA.compareTo(ruleA2));
        assertTrue(ruleA.compareTo(null) < 0);

        assertThrows(NullPointerException.class, () -> new ClassificationRuleId(null));
    }

    @Test
    void testMetadataParseResultStatesAndFiltering() {
        // 1. Absent state
        MetadataParseResult absentResult = MetadataParseResult.absent();
        assertTrue(absentResult instanceof MetadataParseResult.Absent);

        // 2. Valid with target_mod
        JsonObject validWithTarget = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Target Mod Pack",
                  "target_mod": "somemod"
                }
                """).getAsJsonObject();
        MetadataParseResult validTargetRes = CompatibilityMetadata.parse(validWithTarget);
        assertTrue(validTargetRes instanceof MetadataParseResult.Valid);
        assertEquals("somemod", ((MetadataParseResult.Valid) validTargetRes).metadata().targetMod());
        assertEquals("Target Mod Pack", ((MetadataParseResult.Valid) validTargetRes).metadata().name());

        // 3. Valid without target_mod
        JsonObject validNoTarget = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Universal Pack"
                }
                """).getAsJsonObject();
        MetadataParseResult validNoTargetRes = CompatibilityMetadata.parse(validNoTarget);
        assertTrue(validNoTargetRes instanceof MetadataParseResult.Valid);
        assertNull(((MetadataParseResult.Valid) validNoTargetRes).metadata().targetMod());

        // 4. Invalid format: 999
        JsonObject futureFormat = JsonParser.parseString("""
                {
                  "format": 999,
                  "name": "Future Pack"
                }
                """).getAsJsonObject();
        MetadataParseResult invalidFuture = CompatibilityMetadata.parse(futureFormat);
        assertTrue(invalidFuture instanceof MetadataParseResult.Invalid);
        assertTrue(((MetadataParseResult.Invalid) invalidFuture).reason().contains("999"));

        // 5. Invalid format: 0
        JsonObject zeroFormat = JsonParser.parseString("""
                {
                  "format": 0,
                  "name": "Zero Pack"
                }
                """).getAsJsonObject();
        MetadataParseResult invalidZero = CompatibilityMetadata.parse(zeroFormat);
        assertTrue(invalidZero instanceof MetadataParseResult.Invalid);

        // 6. Invalid non-integer format
        JsonObject badFormat = JsonParser.parseString("""
                {
                  "format": "one",
                  "name": "String Format Pack"
                }
                """).getAsJsonObject();
        MetadataParseResult invalidBad = CompatibilityMetadata.parse(badFormat);
        assertTrue(invalidBad instanceof MetadataParseResult.Invalid);

        // 7. Null JsonObject
        MetadataParseResult invalidNull = CompatibilityMetadata.parse(null);
        assertTrue(invalidNull instanceof MetadataParseResult.Invalid);

        // 8. Integration with FoodClassificationJsonLoader:
        // Namespace with invalid metadata in skippedPacks must be skipped completely, not loaded as legacy
        ResourceLocation futureItem = ResourceLocation.parse("futurepack:food_classifications/item");
        JsonObject itemJson = JsonParser.parseString("""
                {
                  "status": "HALAL",
                  "reason": "should_be_skipped"
                }
                """).getAsJsonObject();

        Map<String, CompatibilityMetadata> activePacks = Map.of();
        Map<String, CompatibilityMetadata> skippedPacks = Map.of(
                "futurepack", new CompatibilityMetadata(-1, "Invalid Metadata (Unsupported format version: 999)", null)
        );

        Map<ResourceLocation, List<FoodClassification>> parsed = FoodClassificationJsonLoader.parseAllMulti(
                Map.of(futureItem, itemJson),
                activePacks,
                skippedPacks
        );
        assertTrue(parsed.isEmpty(), "Entries from namespace with invalid metadata in skippedPacks must be skipped");
    }

    @Test
    void testSameProviderSameNamespaceRuleDeterministicConflict() {
        ResourceLocation itemLoc = ResourceLocation.parse("examplemod:shared_berry");
        JsonObject ruleAJson = JsonParser.parseString("""
                {
                  "item": "examplemod:shared_berry",
                  "status": "HALAL",
                  "reason": "rule_a_reason"
                }
                """).getAsJsonObject();
        JsonObject ruleBJson = JsonParser.parseString("""
                {
                  "item": "examplemod:shared_berry",
                  "status": "RESTRICTED",
                  "reason": "rule_b_reason"
                }
                """).getAsJsonObject();

        ResourceLocation fileA = ResourceLocation.parse("custompack:food_classifications/rule_a");
        ResourceLocation fileB = ResourceLocation.parse("custompack:food_classifications/rule_b");

        // Parse with loader
        Map<ResourceLocation, List<FoodClassification>> parsed = FoodClassificationJsonLoader.parseAllMulti(
                Map.of(fileA, ruleAJson, fileB, ruleBJson)
        );

        List<FoodClassification> list = parsed.get(itemLoc);
        assertNotNull(list);
        assertEquals(2, list.size());

        // Check rule provenance
        assertEquals(ClassificationRuleId.parse("custompack:food_classifications/rule_a"), list.get(0).ruleId());
        assertEquals(ClassificationRuleId.parse("custompack:food_classifications/rule_b"), list.get(1).ruleId());

        FoodClassificationRegistry.setDatapackMultiClassifications(parsed);

        ClassificationResolution res = FoodClassifier.resolve(itemLoc);
        assertNotNull(res);
        assertTrue(res.conflicted(), "Different statuses under same provider must flag conflict");
        assertEquals(2, res.candidates().size());

        // Winner must be rule_a deterministically
        assertEquals(ClassificationRuleId.parse("custompack:food_classifications/rule_a"), res.selected().ruleId());
        assertEquals(FoodStatus.HALAL, res.selected().status());
        assertEquals("rule_a_reason", res.selected().reason());

        // Fast path equivalence
        FoodClassification fast = FoodClassifier.classify(itemLoc);
        assertEquals(res.selected(), fast);

        // Reverse map order to verify order independence
        Map<ResourceLocation, List<FoodClassification>> reversedParsed = FoodClassificationJsonLoader.parseAllMulti(
                Map.of(fileB, ruleBJson, fileA, ruleAJson)
        );
        FoodClassificationRegistry.setDatapackMultiClassifications(reversedParsed);

        ClassificationResolution resReversed = FoodClassifier.resolve(itemLoc);
        assertEquals(res.selected(), resReversed.selected(), "Resolution must be deterministic regardless of input order");
    }

    @Test
    void testPerfectIdentityCollisionDeterministicFinalTieBreaker() {
        ResourceLocation berryId = ResourceLocation.parse("examplemod:collision_berry");
        ClassificationProviderId providerId = ClassificationProviderId.parse("custompack:datapack");
        ClassificationRuleId ruleId = ClassificationRuleId.parse("custompack:food_classifications/same_rule");

        FoodClassification candidateHalal = new FoodClassification(
                FoodStatus.HALAL,
                "reason_z",
                ClassificationSource.DATAPACK,
                providerId,
                ClassificationPriority.DATAPACK,
                ruleId
        );
        FoodClassification candidateRestricted = new FoodClassification(
                FoodStatus.RESTRICTED,
                "reason_a",
                ClassificationSource.DATAPACK,
                providerId,
                ClassificationPriority.DATAPACK,
                ruleId
        );

        // Put in list in reverse order
        List<FoodClassification> list = List.of(candidateRestricted, candidateHalal);
        FoodClassificationRegistry.setDatapackMultiClassifications(Map.of(berryId, list));

        ClassificationResolution res = FoodClassifier.resolve(berryId);
        assertTrue(res.conflicted());
        assertEquals(2, res.candidates().size());

        // HALAL ("HALAL") < RESTRICTED ("RESTRICTED") lexicographically
        assertEquals(FoodStatus.HALAL, res.selected().status());
        assertEquals(FoodClassifier.classify(berryId), res.selected());

        // Test same status tie-breaker by reason
        FoodClassification cReasonA = new FoodClassification(
                FoodStatus.HALAL,
                "reason_a",
                ClassificationSource.DATAPACK,
                providerId,
                ClassificationPriority.DATAPACK,
                ruleId
        );
        FoodClassification cReasonB = new FoodClassification(
                FoodStatus.HALAL,
                "reason_b",
                ClassificationSource.DATAPACK,
                providerId,
                ClassificationPriority.DATAPACK,
                ruleId
        );

        FoodClassificationRegistry.setDatapackMultiClassifications(Map.of(berryId, List.of(cReasonB, cReasonA)));
        ClassificationResolution resReason = FoodClassifier.resolve(berryId);
        assertFalse(resReason.conflicted());
        assertEquals("reason_a", resReason.selected().reason());
        assertEquals(FoodClassifier.classify(berryId), resReason.selected());
    }

    @Test
    void testSingleGenerationTransactionalConsistencyUnderConcurrentSwapping() throws Exception {
        ResourceLocation item1 = ResourceLocation.parse("testmod:item_one");
        ResourceLocation item2 = ResourceLocation.parse("testmod:item_two");

        ClassificationProviderId provA = ClassificationProviderId.parse("gen_a:pack");
        ClassificationProviderId provB = ClassificationProviderId.parse("gen_b:pack");

        // Generation A: item1 is HALAL, item2 is RESTRICTED
        ClassificationRuntimeState stateA = new ClassificationRuntimeState(
                Map.of(
                        item1, List.of(new FoodClassification(FoodStatus.HALAL, "gen_a_1", ClassificationSource.DATAPACK, provA, ClassificationPriority.DATAPACK)),
                        item2, List.of(new FoodClassification(FoodStatus.RESTRICTED, "gen_a_2", ClassificationSource.DATAPACK, provA, ClassificationPriority.DATAPACK))
                ),
                Map.of(),
                Map.of("gen_a", new CompatibilityMetadata(1, "Gen A", null)),
                Map.of()
        );

        // Generation B: item1 is RESTRICTED, item2 is HALAL
        ClassificationRuntimeState stateB = new ClassificationRuntimeState(
                Map.of(
                        item1, List.of(new FoodClassification(FoodStatus.RESTRICTED, "gen_b_1", ClassificationSource.DATAPACK, provB, ClassificationPriority.DATAPACK)),
                        item2, List.of(new FoodClassification(FoodStatus.HALAL, "gen_b_2", ClassificationSource.DATAPACK, provB, ClassificationPriority.DATAPACK))
                ),
                Map.of(),
                Map.of("gen_b", new CompatibilityMetadata(1, "Gen B", null)),
                Map.of()
        );

        FoodClassificationRegistry.applyRuntimeState(stateA);

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Throwable> readerError = new AtomicReference<>();
        int swapperIterations = 1000;

        // Swapper thread rapidly toggles runtime state
        Thread swapper = new Thread(() -> {
            for (int i = 0; i < swapperIterations; i++) {
                if (i % 2 == 0) {
                    FoodClassificationRegistry.applyRuntimeState(stateB);
                } else {
                    FoodClassificationRegistry.applyRuntimeState(stateA);
                }
                Thread.yield();
            }
            running.set(false);
        }, "swapper-test-thread");

        // Reader thread verifying single-generation consistency
        Thread reader = new Thread(() -> {
            while (running.get()) {
                try {
                    CompatibilitySnapshot snapshot = FoodCompatibilityManager.getActiveSnapshot();
                    ClassificationResolution res1 = snapshot.resolve(item1);
                    ClassificationResolution res2 = snapshot.resolve(item2);

                    FoodClassification c1 = res1.selected();
                    FoodClassification c2 = res2.selected();

                    // Equivalence check within snapshot
                    assertEquals(c1, snapshot.classify(item1));
                    assertEquals(c2, snapshot.classify(item2));

                    // Single-generation invariant:
                    // If c1 is Gen A (HALAL), c2 MUST be Gen A (RESTRICTED).
                    // If c1 is Gen B (RESTRICTED), c2 MUST be Gen B (HALAL).
                    if (c1.status() == FoodStatus.HALAL) {
                        assertEquals(FoodStatus.RESTRICTED, c2.status(),
                                "Snapshot observed hybrid generation: c1 was Gen A (HALAL) but c2 was not Gen A (RESTRICTED)!");
                        assertEquals("gen_a_1", c1.reason());
                        assertEquals("gen_a_2", c2.reason());
                    } else if (c1.status() == FoodStatus.RESTRICTED) {
                        assertEquals(FoodStatus.HALAL, c2.status(),
                                "Snapshot observed hybrid generation: c1 was Gen B (RESTRICTED) but c2 was not Gen B (HALAL)!");
                        assertEquals("gen_b_1", c1.reason());
                        assertEquals("gen_b_2", c2.reason());
                    } else {
                        throw new IllegalStateException("Unexpected status: " + c1.status());
                    }
                } catch (Throwable t) {
                    readerError.compareAndSet(null, t);
                    break;
                }
            }
        }, "reader-test-thread");

        reader.start();
        swapper.start();

        swapper.join(10000);
        reader.join(10000);

        if (readerError.get() != null) {
            throw new AssertionError("Single-generation consistency failed", readerError.get());
        }
    }

    @Test
    void testComprehensiveFastPathEquivalence() {
        // 1. Builtin items
        assertEquals(FoodClassifier.resolve(appleId).selected(), FoodClassifier.classify(appleId));
        assertEquals(FoodClassifier.resolve(porkId).selected(), FoodClassifier.classify(porkId));

        // 2. Unknown item
        assertEquals(FoodClassifier.resolve(unknownId).selected(), FoodClassifier.classify(unknownId));

        // 3. User override
        ResourceLocation userItem = ResourceLocation.parse("testmod:user_item");
        FoodClassificationRegistry.registerUserOverride(
                userItem,
                new FoodClassification(FoodStatus.RESTRICTED, "user_rule", ClassificationSource.USER_OVERRIDE)
        );
        assertEquals(FoodClassifier.resolve(userItem).selected(), FoodClassifier.classify(userItem));

        // 4. Datapack single entry
        ResourceLocation dpItem = ResourceLocation.parse("testmod:dp_item");
        FoodClassificationRegistry.registerDatapackEntry(
                dpItem,
                new FoodClassification(FoodStatus.HALAL, "dp_rule", ClassificationSource.DATAPACK)
        );
        assertEquals(FoodClassifier.resolve(dpItem).selected(), FoodClassifier.classify(dpItem));

        // 5. Datapack conflict between different providers
        ResourceLocation dpConflictItem = ResourceLocation.parse("testmod:dp_conflict");
        FoodClassificationRegistry.setDatapackMultiClassifications(Map.of(
                dpConflictItem, List.of(
                        new FoodClassification(FoodStatus.HALAL, "reason_b", ClassificationSource.DATAPACK, ClassificationProviderId.parse("provider_b:pack"), ClassificationPriority.DATAPACK),
                        new FoodClassification(FoodStatus.RESTRICTED, "reason_a", ClassificationSource.DATAPACK, ClassificationProviderId.parse("provider_a:pack"), ClassificationPriority.DATAPACK)
                )
        ));
        ClassificationResolution dpConflictRes = FoodClassifier.resolve(dpConflictItem);
        assertTrue(dpConflictRes.conflicted());
        assertEquals(dpConflictRes.selected(), FoodClassifier.classify(dpConflictItem));

        // 6. Datapack conflict within same provider with distinct rule IDs
        ResourceLocation sameProvConflictItem = ResourceLocation.parse("testmod:same_prov_conflict");
        ClassificationProviderId commonProv = ClassificationProviderId.parse("common:pack");
        FoodClassificationRegistry.setDatapackMultiClassifications(Map.of(
                sameProvConflictItem, List.of(
                        new FoodClassification(FoodStatus.RESTRICTED, "beta", ClassificationSource.DATAPACK, commonProv, ClassificationPriority.DATAPACK, ClassificationRuleId.parse("common:food_classifications/rule_b")),
                        new FoodClassification(FoodStatus.HALAL, "alpha", ClassificationSource.DATAPACK, commonProv, ClassificationPriority.DATAPACK, ClassificationRuleId.parse("common:food_classifications/rule_a"))
                )
        ));
        ClassificationResolution sameProvRes = FoodClassifier.resolve(sameProvConflictItem);
        assertTrue(sameProvRes.conflicted());
        assertEquals(sameProvRes.selected(), FoodClassifier.classify(sameProvConflictItem));

        // 7. Equal priority, provider, rule ID tie-break
        ResourceLocation collisionItem = ResourceLocation.parse("testmod:collision_item");
        ClassificationRuleId commonRule = ClassificationRuleId.parse("common:food_classifications/single_rule");
        FoodClassificationRegistry.setDatapackMultiClassifications(Map.of(
                collisionItem, List.of(
                        new FoodClassification(FoodStatus.RESTRICTED, "strict", ClassificationSource.DATAPACK, commonProv, ClassificationPriority.DATAPACK, commonRule),
                        new FoodClassification(FoodStatus.HALAL, "permissive", ClassificationSource.DATAPACK, commonProv, ClassificationPriority.DATAPACK, commonRule)
                )
        ));
        ClassificationResolution collisionRes = FoodClassifier.resolve(collisionItem);
        assertTrue(collisionRes.conflicted());
        assertEquals(collisionRes.selected(), FoodClassifier.classify(collisionItem));

        // 8. Custom provider throwing exception
        ClassificationProviderId brokenId = ClassificationProviderId.parse("broken:provider");
        FoodCompatibilityManager.registerProvider(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() { return brokenId; }
            @Override
            public ClassificationPriority priority() { return ClassificationPriority.USER_OVERRIDE; }
            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                throw new RuntimeException("Simulated crash");
            }
        });
        assertEquals(FoodClassifier.resolve(appleId).selected(), FoodClassifier.classify(appleId));
    }
}
