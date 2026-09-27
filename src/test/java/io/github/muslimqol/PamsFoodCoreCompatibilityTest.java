package io.github.muslimqol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.muslimqol.api.ClassificationPriority;
import io.github.muslimqol.api.ClassificationRuleId;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.compat.CompatibilityMetadata;
import io.github.muslimqol.compat.FoodCompatibilityManager;
import io.github.muslimqol.compat.MetadataParseResult;
import io.github.muslimqol.data.FoodClassificationJsonLoader;
import io.github.muslimqol.food.FoodClassificationRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
 * Validates the bundled Pam's HarvestCraft 2 Food Core compatibility pack data and behavior.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Compatibility metadata structure and target mod declaration.</li>
 *   <li>Integrity of all bundled food classification JSON files.</li>
 *   <li>Zero internal conflicts or duplicate item declarations.</li>
 *   <li>Deterministic loading when Pam's HarvestCraft 2 Food Core is present.</li>
 *   <li>Safe skipping when Pam's HarvestCraft 2 Food Core is absent.</li>
 *   <li>Exact bidirectional equality with frozen reference manifest (180 items).</li>
 *   <li>Classification rules: pork (RESTRICTED), variable stock/dishes (DOUBTFUL),
 *       meat (UNKNOWN), fish & plants & permitted recipes (HALAL).</li>
 * </ul>
 */
class PamsFoodCoreCompatibilityTest {

    private static final String NAMESPACE = "muslimqol_pamhc2foodcore";
    private static final String TARGET_MOD = "pamhc2foodcore";

    private static final String[] BUNDLED_JSON_FILES = {
            "pork.json",
            "doubtful.json",
            "meat_unknown.json",
            "fish.json",
            "plants.json",
            "prepared_meals.json"
    };

    @BeforeAll
    static void init() {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Exception | ExceptionInInitializerError ignored) {
            // Tolerant initialization for isolated unit test runners.
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
            assertEquals("MuslimQoL Pam's HarvestCraft 2 Food Core Compatibility", meta.name());
            assertEquals(TARGET_MOD, meta.targetMod());
            assertEquals("1.0.4", meta.targetVersion());
            assertEquals("acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9", meta.referenceJarSha256());
        }
    }

    // ── 2. Data File Integrity & Exact Coverage Validation ──────────────────────

    @Test
    void testAllBundledClassificationFilesAreValidAndMatchReferenceManifest() throws Exception {
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

                    // Must target pamhc2foodcore namespace only
                    assertEquals(TARGET_MOD, itemId.getNamespace(),
                            "Item must be in 'pamhc2foodcore' namespace, found: " + itemStr);

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

        // Complete audit set of 180 edible items verified against frozen reference manifest
        InputStream refStream = getClass().getResourceAsStream("/reference/pamhc2foodcore-1.0.4-edible-items.json");
        assertNotNull(refStream, "Frozen reference manifest must exist in test resources");
        Set<ResourceLocation> referenceItems = new HashSet<>();
        try (var refReader = new InputStreamReader(refStream, StandardCharsets.UTF_8)) {
            JsonObject refObj = JsonParser.parseReader(refReader).getAsJsonObject();
            assertEquals("pamhc2foodcore", refObj.get("mod").getAsString());
            assertEquals("1.21.1-1.0.4", refObj.get("version").getAsString());
            assertEquals("acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9",
                    refObj.get("jar_sha256").getAsString());
            assertEquals(180, refObj.get("total_items").getAsInt());
            JsonArray itemsArray = refObj.getAsJsonArray("items");
            for (JsonElement itemElem : itemsArray) {
                referenceItems.add(ResourceLocation.parse(itemElem.getAsString()));
            }
        }

        assertEquals(180, totalItemsCount, "Total items count must be exactly 180");
        assertEquals(referenceItems.size(), totalItemsCount,
                "Total item count must match reference manifest count");
        assertEquals(referenceItems, declaredItems,
                "Declared items must exactly equal the frozen reference manifest set (bidirectional Set equality)");
    }

    // ── 3. Loader & Namespace Filtering ────────────────────────────────────────

    @Test
    void testNamespaceLoadsWhenTargetModIsPresent() {
        Map<ResourceLocation, JsonElement> jsonMap = loadAllBundledJson();
        Map<String, CompatibilityMetadata> activePacks = new HashMap<>();
        Map<String, CompatibilityMetadata> skippedPacks = new HashMap<>();

        CompatibilityMetadata meta = new CompatibilityMetadata(1, "Pam's Food Core Compatibility", TARGET_MOD);

        // Simulate pamhc2foodcore IS loaded
        FoodCompatibilityManager.setModLoadedChecker(mod -> TARGET_MOD.equals(mod));
        if (FoodCompatibilityManager.isModLoaded(meta.targetMod())) {
            activePacks.put(NAMESPACE, meta);
        } else {
            skippedPacks.put(NAMESPACE, meta);
        }

        Map<ResourceLocation, List<FoodClassification>> parsed =
                FoodClassificationJsonLoader.parseAllMulti(jsonMap, activePacks, skippedPacks);

        assertFalse(parsed.isEmpty(), "Classification entries must load when target mod is present");
        assertEquals(180, parsed.size(), "All 180 Pam's Food Core items must be loaded");

        // Verify representative samples
        // 1. Swine -> RESTRICTED
        ResourceLocation hotdogId = ResourceLocation.parse("pamhc2foodcore:hotdogitem");
        assertNotNull(parsed.get(hotdogId));
        assertEquals(FoodStatus.RESTRICTED, parsed.get(hotdogId).get(0).status());
        assertEquals("swine", parsed.get(hotdogId).get(0).reason());

        ResourceLocation baconEggsId = ResourceLocation.parse("pamhc2foodcore:baconandeggsitem");
        assertNotNull(parsed.get(baconEggsId));
        assertEquals(FoodStatus.RESTRICTED, parsed.get(baconEggsId).get(0).status());
        assertEquals("swine", parsed.get(baconEggsId).get(0).reason());

        // 2. Variable stock -> DOUBTFUL
        ResourceLocation stockId = ResourceLocation.parse("pamhc2foodcore:stockitem");
        assertNotNull(parsed.get(stockId));
        assertEquals(FoodStatus.DOUBTFUL, parsed.get(stockId).get(0).status());
        assertEquals("variable_provenance", parsed.get(stockId).get(0).reason());

        ResourceLocation carrotSoupId = ResourceLocation.parse("pamhc2foodcore:carrotsoupitem");
        assertNotNull(parsed.get(carrotSoupId));
        assertEquals(FoodStatus.DOUBTFUL, parsed.get(carrotSoupId).get(0).status());
        assertEquals("variable_provenance", parsed.get(carrotSoupId).get(0).reason());

        // 3. Unspecified livestock meat -> UNKNOWN
        ResourceLocation chickenDinnerId = ResourceLocation.parse("pamhc2foodcore:chickendinneritem");
        assertNotNull(parsed.get(chickenDinnerId));
        assertEquals(FoodStatus.UNKNOWN, parsed.get(chickenDinnerId).get(0).status());
        assertEquals("unspecified_meat", parsed.get(chickenDinnerId).get(0).reason());

        ResourceLocation burgerId = ResourceLocation.parse("pamhc2foodcore:basichamburgeritem");
        assertNotNull(parsed.get(burgerId));
        assertEquals(FoodStatus.UNKNOWN, parsed.get(burgerId).get(0).status());
        assertEquals("unspecified_meat", parsed.get(burgerId).get(0).reason());

        // 4. Fish -> HALAL
        ResourceLocation fishSandwichId = ResourceLocation.parse("pamhc2foodcore:basicfishsandwichitem");
        assertNotNull(parsed.get(fishSandwichId));
        assertEquals(FoodStatus.HALAL, parsed.get(fishSandwichId).get(0).status());
        assertEquals("fish", parsed.get(fishSandwichId).get(0).reason());

        // 5. Plant-based -> HALAL
        ResourceLocation fruitSaladId = ResourceLocation.parse("pamhc2foodcore:fruitsaladitem");
        assertNotNull(parsed.get(fruitSaladId));
        assertEquals(FoodStatus.HALAL, parsed.get(fruitSaladId).get(0).status());
        assertEquals("plant_based", parsed.get(fruitSaladId).get(0).reason());

        // 6. Audited permitted recipe (dairy / jelly toast) -> HALAL
        ResourceLocation toastId = ResourceLocation.parse("pamhc2foodcore:toastitem");
        assertNotNull(parsed.get(toastId));
        assertEquals(FoodStatus.HALAL, parsed.get(toastId).get(0).status());
        assertEquals("audited_permitted_recipe", parsed.get(toastId).get(0).reason());

        ResourceLocation jellyToastId = ResourceLocation.parse("pamhc2foodcore:applejellytoastitem");
        assertNotNull(parsed.get(jellyToastId));
        assertEquals(FoodStatus.HALAL, parsed.get(jellyToastId).get(0).status());
        assertEquals("audited_permitted_recipe", parsed.get(jellyToastId).get(0).reason());
    }

    @Test
    void testNamespaceSkipsSafelyWhenTargetModIsAbsent() {
        Map<ResourceLocation, JsonElement> jsonMap = loadAllBundledJson();
        Map<String, CompatibilityMetadata> activePacks = new HashMap<>();
        Map<String, CompatibilityMetadata> skippedPacks = new HashMap<>();

        CompatibilityMetadata meta = new CompatibilityMetadata(1, "Pam's Food Core Compatibility", TARGET_MOD);

        // Simulate pamhc2foodcore is NOT loaded
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

        ResourceLocation hotdogId = ResourceLocation.parse("pamhc2foodcore:hotdogitem");
        assertFalse(parsed.containsKey(hotdogId));
    }

    // ── 4. Provenance Tracking ─────────────────────────────────────────────────

    @Test
    void testProvenanceReflectsNamespaceAndRule() {
        Map<ResourceLocation, JsonElement> jsonMap = loadAllBundledJson();
        Map<String, CompatibilityMetadata> activePacks = Map.of(
                NAMESPACE, new CompatibilityMetadata(1, "Pam's Food Core Compatibility", TARGET_MOD)
        );

        Map<ResourceLocation, List<FoodClassification>> parsed =
                FoodClassificationJsonLoader.parseAllMulti(jsonMap, activePacks, Map.of());

        ResourceLocation hotdogId = ResourceLocation.parse("pamhc2foodcore:hotdogitem");
        List<FoodClassification> entries = parsed.get(hotdogId);
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
                NAMESPACE, new CompatibilityMetadata(1, "Pam's Food Core Compatibility", TARGET_MOD)
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

        assertEquals(125, halalCount, "HALAL items count");
        assertEquals(13, restrictedCount, "RESTRICTED items count");
        assertEquals(13, doubtfulCount, "DOUBTFUL items count");
        assertEquals(29, unknownCount, "UNKNOWN items count");
        assertEquals(180, halalCount + restrictedCount + doubtfulCount + unknownCount);
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
