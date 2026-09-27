package io.github.muslimqol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.muslimqol.api.ClassificationPriority;
import io.github.muslimqol.api.ClassificationResolution;
import io.github.muslimqol.api.ClassificationRuleId;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.compat.CompatibilityMetadata;
import io.github.muslimqol.compat.FoodCompatibilityManager;
import io.github.muslimqol.compat.MetadataParseResult;
import io.github.muslimqol.data.FoodClassificationJsonLoader;
import io.github.muslimqol.food.FoodClassificationRegistry;
import io.github.muslimqol.food.FoodClassifier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the bundled Farmer's Delight compatibility pack data and behavior.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Compatibility metadata structure and target mod declaration.</li>
 *   <li>Integrity of all bundled food classification JSON files.</li>
 *   <li>Zero internal conflicts or duplicate item declarations.</li>
 *   <li>Deterministic loading when Farmer's Delight is present.</li>
 *   <li>Safe skipping when Farmer's Delight is absent.</li>
 *   <li>Classification rules for plants (HALAL), pork (RESTRICTED), meat (UNKNOWN), fish (HALAL), and mixtures (DOUBTFUL).</li>
 * </ul>
 */
class FarmersDelightCompatibilityTest {

    private static final String NAMESPACE = "muslimqol_farmersdelight";
    private static final String TARGET_MOD = "farmersdelight";

    private static final String[] BUNDLED_JSON_FILES = {
            "pork.json",
            "carrion.json",
            "meat_unknown.json",
            "seafood_unknown.json",
            "doubtful.json",
            "fish.json",
            "plants.json",
            "prepared_meals.json",
            "desserts.json",
            "drinks.json"
    };

    @BeforeAll
    static void init() {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Exception | ExceptionInInitializerError ignored) {
            // Tolerant initialization: in isolated unit test runners, FeatureFlagLoader may throw
            // ExceptionInInitializerError due to uninitialized LoadingModList.
        }
    }

    @BeforeEach
    @AfterEach
    void resetState() {
        FoodClassificationRegistry.clearAll();
        FoodCompatibilityManager.resetToDefaults();
        FoodCompatibilityManager.setModLoadedChecker(null);
    }

    // ── 1. Metadata Validation ──────────────────────────────────────────────────

    @Test
    void testCompatibilityMetadataIsValid() throws Exception {
        String metaPath = "/data/" + NAMESPACE + "/muslimqol/compatibility.json";
        InputStream stream = getClass().getResourceAsStream(metaPath);
        assertNotNull(stream, "Bundled compatibility.json must exist in resources");

        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement elem = JsonParser.parseReader(reader);
            assertTrue(elem.isJsonObject(), "compatibility.json must be a JSON object");
            JsonObject obj = elem.getAsJsonObject();

            MetadataParseResult result = CompatibilityMetadata.parse(obj);
            assertTrue(result instanceof MetadataParseResult.Valid,
                    "Metadata must parse as valid");

            CompatibilityMetadata meta = ((MetadataParseResult.Valid) result).metadata();
            assertEquals(1, meta.format());
            assertEquals("MuslimQoL Farmer's Delight Compatibility", meta.name());
            assertEquals(TARGET_MOD, meta.targetMod());
            assertEquals("1.3.4", meta.targetVersion());
            assertEquals("139ad7696462c89c03eea463f805abffa552526c5dadaadae221dd9624cb197c", meta.referenceJarSha256());
        }
    }

    // ── 2. Data File Integrity Validation ──────────────────────────────────────

    @Test
    void testAllBundledClassificationFilesAreValid() throws Exception {
        Set<ResourceLocation> declaredItems = new HashSet<>();
        Map<ResourceLocation, String> itemToStatus = new HashMap<>();

        int totalItemsCount = 0;

        for (String fileName : BUNDLED_JSON_FILES) {
            String path = "/data/" + NAMESPACE + "/muslimqol/food_classifications/" + fileName;
            InputStream stream = getClass().getResourceAsStream(path);
            assertNotNull(stream, "File must exist: " + path);

            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement elem = JsonParser.parseReader(reader);
                assertTrue(elem.isJsonObject(), fileName + " root must be a JSON object");
                JsonObject obj = elem.getAsJsonObject();

                assertTrue(obj.has("values") && obj.get("values").isJsonArray(),
                        fileName + " must have a 'values' JSON array");

                JsonArray values = obj.getAsJsonArray("values");
                assertFalse(values.isEmpty(), fileName + " must not have empty 'values' array");

                for (JsonElement itemElem : values) {
                    assertTrue(itemElem.isJsonObject(), "Entry must be a JSON object");
                    JsonObject itemObj = itemElem.getAsJsonObject();

                    // Must have 'item'
                    assertTrue(itemObj.has("item"), "Must specify 'item'");
                    String itemStr = itemObj.get("item").getAsString();
                    ResourceLocation itemId = ResourceLocation.parse(itemStr);

                    // Must target farmersdelight namespace only
                    assertEquals(TARGET_MOD, itemId.getNamespace(),
                            "Item must be in 'farmersdelight' namespace, found: " + itemStr);

                    // Must have valid FoodStatus
                    assertTrue(itemObj.has("status"), "Must specify 'status'");
                    String statusStr = itemObj.get("status").getAsString();
                    FoodStatus status = FoodStatus.valueOf(statusStr.toUpperCase());
                    assertNotNull(status);

                    // Must have non-empty reason
                    assertTrue(itemObj.has("reason"), "Must specify 'reason'");
                    String reason = itemObj.get("reason").getAsString();
                    assertFalse(reason.isBlank(), "Reason must not be blank");

                    // Check for duplicate declarations inside the pack
                    assertFalse(declaredItems.contains(itemId),
                            "Duplicate item declaration in pack: " + itemId);
                    declaredItems.add(itemId);
                    itemToStatus.put(itemId, statusStr);
                    totalItemsCount++;
                }
            }
        }

        // Complete audit set of 89 edible items verified against frozen reference manifest
        InputStream refStream = getClass().getResourceAsStream("/reference/farmers-delight-1.3.4-edible-items.json");
        assertNotNull(refStream, "Frozen reference manifest must exist in test resources");
        Set<ResourceLocation> referenceItems = new HashSet<>();
        try (var refReader = new InputStreamReader(refStream, StandardCharsets.UTF_8)) {
            JsonObject refObj = JsonParser.parseReader(refReader).getAsJsonObject();
            assertEquals("farmersdelight", refObj.get("mod").getAsString());
            assertEquals("1.21.1-1.3.4", refObj.get("version").getAsString());
            assertEquals("139ad7696462c89c03eea463f805abffa552526c5dadaadae221dd9624cb197c",
                    refObj.get("jar_sha256").getAsString());
            JsonArray itemsArray = refObj.getAsJsonArray("items");
            for (JsonElement itemElem : itemsArray) {
                referenceItems.add(ResourceLocation.parse(itemElem.getAsString()));
            }
        }

        assertEquals(referenceItems.size(), totalItemsCount,
                "Total item count must match reference manifest count");
        assertEquals(referenceItems, declaredItems,
                "Declared items must exactly equal the frozen reference manifest set");
    }

    // ── 3. Loader & Namespace Filtering ────────────────────────────────────────

    @Test
    void testNamespaceLoadsWhenTargetModIsPresent() {
        Map<ResourceLocation, JsonElement> jsonMap = loadAllBundledJson();
        Map<String, CompatibilityMetadata> activePacks = new HashMap<>();
        Map<String, CompatibilityMetadata> skippedPacks = new HashMap<>();

        CompatibilityMetadata meta = new CompatibilityMetadata(1, "Farmer's Delight Compatibility", TARGET_MOD);

        // Simulate farmersdelight IS loaded
        FoodCompatibilityManager.setModLoadedChecker(mod -> TARGET_MOD.equals(mod));
        if (FoodCompatibilityManager.isModLoaded(meta.targetMod())) {
            activePacks.put(NAMESPACE, meta);
        } else {
            skippedPacks.put(NAMESPACE, meta);
        }

        Map<ResourceLocation, List<FoodClassification>> parsed =
                FoodClassificationJsonLoader.parseAllMulti(jsonMap, activePacks, skippedPacks);

        assertFalse(parsed.isEmpty(), "Classification entries must load when target mod is present");
        assertEquals(89, parsed.size(), "All 89 Farmer's Delight items must be loaded");

        // Verify representative samples
        ResourceLocation cabbageId = ResourceLocation.parse("farmersdelight:cabbage");
        assertNotNull(parsed.get(cabbageId));
        assertEquals(FoodStatus.HALAL, parsed.get(cabbageId).get(0).status());
        assertEquals("plant_based", parsed.get(cabbageId).get(0).reason());

        ResourceLocation baconId = ResourceLocation.parse("farmersdelight:bacon");
        assertNotNull(parsed.get(baconId));
        assertEquals(FoodStatus.RESTRICTED, parsed.get(baconId).get(0).status());
        assertEquals("swine", parsed.get(baconId).get(0).reason());

        ResourceLocation beefId = ResourceLocation.parse("farmersdelight:minced_beef");
        assertNotNull(parsed.get(beefId));
        assertEquals(FoodStatus.UNKNOWN, parsed.get(beefId).get(0).status());
        assertEquals("unspecified_meat", parsed.get(beefId).get(0).reason());

        ResourceLocation dumplingId = ResourceLocation.parse("farmersdelight:dumplings");
        assertNotNull(parsed.get(dumplingId));
        assertEquals(FoodStatus.DOUBTFUL, parsed.get(dumplingId).get(0).status());
        assertEquals("unknown_ingredients", parsed.get(dumplingId).get(0).reason());

        ResourceLocation codSliceId = ResourceLocation.parse("farmersdelight:cod_slice");
        assertNotNull(parsed.get(codSliceId));
        assertEquals(FoodStatus.HALAL, parsed.get(codSliceId).get(0).status());
        assertEquals("fish", parsed.get(codSliceId).get(0).reason());

        ResourceLocation squidPastaId = ResourceLocation.parse("farmersdelight:squid_ink_pasta");
        assertNotNull(parsed.get(squidPastaId));
        assertEquals(FoodStatus.UNKNOWN, parsed.get(squidPastaId).get(0).status());
        assertEquals("seafood_policy_unspecified", parsed.get(squidPastaId).get(0).reason());

        ResourceLocation friedEggId = ResourceLocation.parse("farmersdelight:fried_egg");
        assertNotNull(parsed.get(friedEggId));
        assertEquals(FoodStatus.HALAL, parsed.get(friedEggId).get(0).status());
        assertEquals("audited_permitted_recipe", parsed.get(friedEggId).get(0).reason());
    }

    @Test
    void testNamespaceSkipsSafelyWhenTargetModIsAbsent() {
        Map<ResourceLocation, JsonElement> jsonMap = loadAllBundledJson();
        Map<String, CompatibilityMetadata> activePacks = new HashMap<>();
        Map<String, CompatibilityMetadata> skippedPacks = new HashMap<>();

        CompatibilityMetadata meta = new CompatibilityMetadata(1, "Farmer's Delight Compatibility", TARGET_MOD);

        // Simulate farmersdelight is NOT loaded
        FoodCompatibilityManager.setModLoadedChecker(mod -> false);
        if (FoodCompatibilityManager.isModLoaded(meta.targetMod())) {
            activePacks.put(NAMESPACE, meta);
        } else {
            skippedPacks.put(NAMESPACE, meta);
        }

        Map<ResourceLocation, List<FoodClassification>> parsed =
                FoodClassificationJsonLoader.parseAllMulti(jsonMap, activePacks, skippedPacks);

        // Entire namespace must be skipped
        assertTrue(parsed.isEmpty(), "All entries from namespace must be skipped when target mod is absent");

        ResourceLocation baconId = ResourceLocation.parse("farmersdelight:bacon");
        assertFalse(parsed.containsKey(baconId));
    }

    // ── 4. Provenance Tracking ─────────────────────────────────────────────────

    @Test
    void testProvenanceReflectsNamespaceAndRule() {
        Map<ResourceLocation, JsonElement> jsonMap = loadAllBundledJson();
        Map<String, CompatibilityMetadata> activePacks = Map.of(
                NAMESPACE, new CompatibilityMetadata(1, "Farmer's Delight Compatibility", TARGET_MOD)
        );

        Map<ResourceLocation, List<FoodClassification>> parsed =
                FoodClassificationJsonLoader.parseAllMulti(jsonMap, activePacks, Map.of());

        ResourceLocation porkSoupId = ResourceLocation.parse("farmersdelight:pumpkin_soup");
        List<FoodClassification> entries = parsed.get(porkSoupId);
        assertNotNull(entries);
        assertFalse(entries.isEmpty());

        FoodClassification classification = entries.get(0);
        assertEquals(FoodStatus.RESTRICTED, classification.status());
        assertEquals("swine", classification.reason());
        assertEquals(NAMESPACE, classification.providerId().id().getNamespace());
        assertEquals("datapack", classification.providerId().id().getPath());

        ClassificationRuleId ruleId = classification.ruleId();
        assertNotNull(ruleId);
        assertEquals(NAMESPACE, ruleId.id().getNamespace());
        assertEquals("food_classifications/pork", ruleId.id().getPath());
        assertEquals(ClassificationPriority.DATAPACK, classification.priority());
    }

    // ── 5. Status Category Distribution ────────────────────────────────────────

    @Test
    void testCategoryCountsMatchAudit() {
        Map<ResourceLocation, JsonElement> jsonMap = loadAllBundledJson();
        Map<String, CompatibilityMetadata> activePacks = Map.of(
                NAMESPACE, new CompatibilityMetadata(1, "Farmer's Delight Compatibility", TARGET_MOD)
        );

        Map<ResourceLocation, List<FoodClassification>> parsed =
                FoodClassificationJsonLoader.parseAllMulti(jsonMap, activePacks, Map.of());

        int halalCount = 0;
        int restrictedCount = 0;
        int doubtfulCount = 0;
        int unknownCount = 0;

        for (List<FoodClassification> list : parsed.values()) {
            FoodStatus s = list.get(0).status();
            switch (s) {
                case HALAL -> halalCount++;
                case RESTRICTED -> restrictedCount++;
                case DOUBTFUL -> doubtfulCount++;
                case UNKNOWN -> unknownCount++;
            }
        }

        assertEquals(52, halalCount, "HALAL items count");
        assertEquals(11, restrictedCount, "RESTRICTED items count");
        assertEquals(5, doubtfulCount, "DOUBTFUL items count");
        assertEquals(21, unknownCount, "UNKNOWN items count");
        assertEquals(89, halalCount + restrictedCount + doubtfulCount + unknownCount);
    }

    @Test
    void testSquidInkPastaProvenance() {
        Map<ResourceLocation, JsonElement> jsonMap = loadAllBundledJson();
        Map<String, CompatibilityMetadata> activePacks = Map.of(
                NAMESPACE, new CompatibilityMetadata(1, "Farmer's Delight Compatibility", TARGET_MOD)
        );

        Map<ResourceLocation, List<FoodClassification>> parsed =
                FoodClassificationJsonLoader.parseAllMulti(jsonMap, activePacks, Map.of());

        ResourceLocation squidPastaId = ResourceLocation.parse("farmersdelight:squid_ink_pasta");
        List<FoodClassification> entries = parsed.get(squidPastaId);
        assertNotNull(entries);
        assertFalse(entries.isEmpty());

        FoodClassification classification = entries.get(0);
        assertEquals(FoodStatus.UNKNOWN, classification.status());
        assertEquals("seafood_policy_unspecified", classification.reason());
        assertEquals(NAMESPACE, classification.providerId().id().getNamespace());

        ClassificationRuleId ruleId = classification.ruleId();
        assertNotNull(ruleId);
        assertEquals(NAMESPACE, ruleId.id().getNamespace());
        assertEquals("food_classifications/seafood_unknown", ruleId.id().getPath());
        assertEquals(ClassificationPriority.DATAPACK, classification.priority());
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private Map<ResourceLocation, JsonElement> loadAllBundledJson() {
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        for (String fileName : BUNDLED_JSON_FILES) {
            String path = "/data/" + NAMESPACE + "/muslimqol/food_classifications/" + fileName;
            try (InputStream stream = getClass().getResourceAsStream(path)) {
                if (stream != null) {
                    try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                        JsonElement elem = JsonParser.parseReader(reader);
                        String strippedName = fileName.replace(".json", "");
                        ResourceLocation fileLoc = ResourceLocation.fromNamespaceAndPath(NAMESPACE, strippedName);
                        map.put(fileLoc, elem);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to read " + path, e);
            }
        }
        return map;
    }
}
