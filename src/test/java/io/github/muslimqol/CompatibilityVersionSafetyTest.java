package io.github.muslimqol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.muslimqol.api.ClassificationPriority;
import io.github.muslimqol.api.ClassificationResolution;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.command.MuslimQolCommands;
import io.github.muslimqol.compat.ClassificationRuntimeState;
import io.github.muslimqol.compat.CompatibilityMetadata;
import io.github.muslimqol.compat.CompatibilityPackState;
import io.github.muslimqol.compat.CompatibilitySnapshot;
import io.github.muslimqol.compat.CompatibilityVerificationStatus;
import io.github.muslimqol.compat.FoodCompatibilityManager;
import io.github.muslimqol.compat.MetadataParseResult;
import io.github.muslimqol.compat.ModVersionResolver;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityVersionSafetyTest {

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
        FoodCompatibilityManager.setModVersionResolver(null);
    }

    // ── 1. Backwards Compatibility ──────────────────────────────────────────────

    @Test
    void testBackwardsCompatibilityLegacyPack() {
        JsonObject legacyJson = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Legacy Pack",
                  "target_mod": "examplemod"
                }
                """).getAsJsonObject();

        MetadataParseResult result = CompatibilityMetadata.parse(legacyJson);
        assertTrue(result instanceof MetadataParseResult.Valid, "Legacy metadata must parse as valid");

        CompatibilityMetadata meta = ((MetadataParseResult.Valid) result).metadata();
        assertEquals(1, meta.format());
        assertEquals("Legacy Pack", meta.name());
        assertEquals("examplemod", meta.targetMod());
        assertNull(meta.targetVersion(), "target_version must be null when absent");
        assertNull(meta.referenceJarSha256(), "reference_jar_sha256 must be null when absent");

        // Evaluates as VERIFIED legacy when mod is loaded
        FoodCompatibilityManager.setModLoadedChecker("examplemod"::equals);
        CompatibilityPackState state = FoodCompatibilityManager.evaluatePack("legacypack", meta);
        assertTrue(state.isActive());
        assertTrue(state.isVerified());
        assertEquals(CompatibilityVerificationStatus.VERIFIED, state.verificationStatus());
    }

    // ── 2. Exact Version Matching ───────────────────────────────────────────────

    @Test
    void testExactVersionMatching() {
        CompatibilityMetadata meta = new CompatibilityMetadata(
                1, "Test Mod Pack", "testmod", "1.0.4", "acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9"
        );

        // A. Declared 1.0.4, Installed 1.0.4 -> VERIFIED
        FoodCompatibilityManager.setModLoadedChecker("testmod"::equals);
        FoodCompatibilityManager.setModVersionResolver(mod -> "testmod".equals(mod) ? Optional.of("1.0.4") : Optional.empty());

        CompatibilityPackState verifiedState = FoodCompatibilityManager.evaluatePack("testpack", meta);
        assertTrue(verifiedState.isActive());
        assertTrue(verifiedState.isVerified());
        assertFalse(verifiedState.isUnverified());
        assertFalse(verifiedState.isSkipped());
        assertEquals("1.0.4", verifiedState.installedVersion());
        assertEquals(CompatibilityVerificationStatus.VERIFIED, verifiedState.verificationStatus());

        // B. Declared 1.0.4, Installed 1.0.5 -> UNVERIFIED (remains active)
        FoodCompatibilityManager.setModVersionResolver(mod -> "testmod".equals(mod) ? Optional.of("1.0.5") : Optional.empty());

        CompatibilityPackState unverifiedState = FoodCompatibilityManager.evaluatePack("testpack", meta);
        assertTrue(unverifiedState.isActive(), "UNVERIFIED pack remains active by default");
        assertFalse(unverifiedState.isVerified());
        assertTrue(unverifiedState.isUnverified());
        assertFalse(unverifiedState.isSkipped());
        assertEquals("1.0.5", unverifiedState.installedVersion());
        assertEquals(CompatibilityVerificationStatus.UNVERIFIED, unverifiedState.verificationStatus());

        // C. Declared 1.0.4, Installed version unavailable -> UNVERIFIED
        FoodCompatibilityManager.setModVersionResolver(mod -> Optional.empty());

        CompatibilityPackState unavailableState = FoodCompatibilityManager.evaluatePack("testpack", meta);
        assertTrue(unavailableState.isActive());
        assertTrue(unavailableState.isUnverified());
        assertFalse(unavailableState.isVerified());
        assertNull(unavailableState.installedVersion());
        assertEquals(CompatibilityVerificationStatus.UNVERIFIED, unavailableState.verificationStatus());

        // D. No target_version, installed any version -> VERIFIED (legacy)
        CompatibilityMetadata unversionedMeta = new CompatibilityMetadata(1, "Unversioned Pack", "testmod", null, null);
        CompatibilityPackState unversionedState = FoodCompatibilityManager.evaluatePack("unversioned", unversionedMeta);
        assertTrue(unversionedState.isActive());
        assertTrue(unversionedState.isVerified());
        assertFalse(unversionedState.isUnverified());

        // E. Target mod absent -> SKIPPED
        FoodCompatibilityManager.setModLoadedChecker(mod -> false);

        CompatibilityPackState skippedState = FoodCompatibilityManager.evaluatePack("testpack", meta);
        assertFalse(skippedState.isActive());
        assertTrue(skippedState.isSkipped());
        assertFalse(skippedState.isVerified());
        assertFalse(skippedState.isUnverified());
        assertEquals(CompatibilityVerificationStatus.SKIPPED, skippedState.verificationStatus());
    }

    // ── 3. Malformed Metadata Parsing ───────────────────────────────────────────

    @Test
    void testMalformedMetadataParsing() {
        // target_version is a JSON object instead of string
        JsonObject objVer = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Bad Target Version",
                  "target_mod": "testmod",
                  "target_version": {}
                }
                """).getAsJsonObject();
        MetadataParseResult res1 = CompatibilityMetadata.parse(objVer);
        assertTrue(res1 instanceof MetadataParseResult.Invalid, "Object target_version must fail");

        // reference_jar_sha256 is a JSON array instead of string
        JsonObject arrSha = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Bad SHA",
                  "target_mod": "testmod",
                  "reference_jar_sha256": []
                }
                """).getAsJsonObject();
        MetadataParseResult res2 = CompatibilityMetadata.parse(arrSha);
        assertTrue(res2 instanceof MetadataParseResult.Invalid, "Array reference_jar_sha256 must fail");

        // target_mod is a JSON object
        JsonObject objMod = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Bad Mod",
                  "target_mod": {}
                }
                """).getAsJsonObject();
        MetadataParseResult res3 = CompatibilityMetadata.parse(objMod);
        assertTrue(res3 instanceof MetadataParseResult.Invalid, "Object target_mod must fail");

        // name is a number
        JsonObject numName = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": 12345
                }
                """).getAsJsonObject();
        MetadataParseResult res4 = CompatibilityMetadata.parse(numName);
        assertTrue(res4 instanceof MetadataParseResult.Invalid, "Number name must fail");
    }

    // ── 4. SHA-256 Syntax Validation ────────────────────────────────────────────

    @Test
    void testSha256SyntaxValidation() {
        String valid64 = "acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9";

        // Exactly 64 lowercase hex characters
        JsonObject validJson = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Valid SHA",
                  "reference_jar_sha256": "acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9"
                }
                """).getAsJsonObject();
        MetadataParseResult resValid = CompatibilityMetadata.parse(validJson);
        assertTrue(resValid instanceof MetadataParseResult.Valid);
        assertEquals(valid64, ((MetadataParseResult.Valid) resValid).metadata().referenceJarSha256());

        // Uppercase normalized to lowercase
        JsonObject upperJson = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Upper SHA",
                  "reference_jar_sha256": "ACD5DD380EAFC3F317B67231A2355C204CAB254DFDF4AB1F25C084FDD3D317B9"
                }
                """).getAsJsonObject();
        MetadataParseResult resUpper = CompatibilityMetadata.parse(upperJson);
        assertTrue(resUpper instanceof MetadataParseResult.Valid);
        assertEquals(valid64, ((MetadataParseResult.Valid) resUpper).metadata().referenceJarSha256());

        // Too short (63 chars)
        JsonObject shortJson = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Short SHA",
                  "reference_jar_sha256": "acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b"
                }
                """).getAsJsonObject();
        assertTrue(CompatibilityMetadata.parse(shortJson) instanceof MetadataParseResult.Invalid);

        // Too long (65 chars)
        JsonObject longJson = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Long SHA",
                  "reference_jar_sha256": "acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b99"
                }
                """).getAsJsonObject();
        assertTrue(CompatibilityMetadata.parse(longJson) instanceof MetadataParseResult.Invalid);

        // Non-hex character 'z'
        JsonObject nonHexJson = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Non-Hex SHA",
                  "reference_jar_sha256": "zcd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9"
                }
                """).getAsJsonObject();
        assertTrue(CompatibilityMetadata.parse(nonHexJson) instanceof MetadataParseResult.Invalid);

        // Blank or JSON null -> null (allowed)
        JsonObject blankJson = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Blank SHA",
                  "reference_jar_sha256": "   "
                }
                """).getAsJsonObject();
        MetadataParseResult resBlank = CompatibilityMetadata.parse(blankJson);
        assertTrue(resBlank instanceof MetadataParseResult.Valid);
        assertNull(((MetadataParseResult.Valid) resBlank).metadata().referenceJarSha256());

        JsonObject nullJson = JsonParser.parseString("""
                {
                  "format": 1,
                  "name": "Null SHA",
                  "reference_jar_sha256": null
                }
                """).getAsJsonObject();
        MetadataParseResult resNull = CompatibilityMetadata.parse(nullJson);
        assertTrue(resNull instanceof MetadataParseResult.Valid);
        assertNull(((MetadataParseResult.Valid) resNull).metadata().referenceJarSha256());
    }

    // ── 5. Snapshot States & Mutually Exclusive Sets ────────────────────────────

    @Test
    void testSnapshotStatesAndExclusivity() {
        CompatibilityMetadata metaVerified = new CompatibilityMetadata(1, "Verified Pack", "mod_a", "1.0.0", null);
        CompatibilityMetadata metaUnverified = new CompatibilityMetadata(1, "Unverified Pack", "mod_b", "2.0.0", null);
        CompatibilityMetadata metaSkipped = new CompatibilityMetadata(1, "Skipped Pack", "mod_c", "3.0.0", null);

        FoodCompatibilityManager.setModLoadedChecker(mod -> "mod_a".equals(mod) || "mod_b".equals(mod));
        FoodCompatibilityManager.setModVersionResolver(mod -> {
            if ("mod_a".equals(mod)) return Optional.of("1.0.0");
            if ("mod_b".equals(mod)) return Optional.of("2.0.1"); // Mismatch
            return Optional.empty();
        });

        CompatibilityPackState stateA = FoodCompatibilityManager.evaluatePack("pack_a", metaVerified);
        CompatibilityPackState stateB = FoodCompatibilityManager.evaluatePack("pack_b", metaUnverified);
        CompatibilityPackState stateC = FoodCompatibilityManager.evaluatePack("pack_c", metaSkipped);

        assertEquals(CompatibilityVerificationStatus.VERIFIED, stateA.verificationStatus());
        assertEquals(CompatibilityVerificationStatus.UNVERIFIED, stateB.verificationStatus());
        assertEquals(CompatibilityVerificationStatus.SKIPPED, stateC.verificationStatus());

        ClassificationRuntimeState runtimeState = new ClassificationRuntimeState(
                Map.of(),
                Map.of(),
                Map.of("pack_a", metaVerified, "pack_b", metaUnverified),
                Map.of("pack_c", metaSkipped),
                Map.of("pack_a", stateA, "pack_b", stateB, "pack_c", stateC)
        );

        CompatibilitySnapshot snapshot = new CompatibilitySnapshot(List.of(), runtimeState);

        // 1. Verified pack appears in verified list
        assertTrue(snapshot.getVerifiedPacks().containsKey("pack_a"));
        assertFalse(snapshot.getVerifiedPacks().containsKey("pack_b"));
        assertFalse(snapshot.getVerifiedPacks().containsKey("pack_c"));

        // 2. Unverified pack appears in unverified list
        assertFalse(snapshot.getUnverifiedPacks().containsKey("pack_a"));
        assertTrue(snapshot.getUnverifiedPacks().containsKey("pack_b"));
        assertFalse(snapshot.getUnverifiedPacks().containsKey("pack_c"));

        // 3. Absent mod pack appears in skipped list
        assertTrue(snapshot.getSkippedPacks().containsKey("pack_c"));
        assertFalse(snapshot.getActivePacks().containsKey("pack_c"));

        // 4. Mutually exclusive sets: no overlap
        for (String ns : snapshot.getPackStates().keySet()) {
            int memberships = 0;
            if (snapshot.getVerifiedPacks().containsKey(ns)) memberships++;
            if (snapshot.getUnverifiedPacks().containsKey(ns)) memberships++;
            if (snapshot.getSkippedPacks().containsKey(ns)) memberships++;
            assertEquals(1, memberships, "Pack '" + ns + "' must occupy exactly one verification status");
        }
    }

    // ── 6. Diagnostic Formatting Helper ─────────────────────────────────────────

    @Test
    void testCommandDiagnosticFormatting() {
        // Verified pack
        CompatibilityMetadata vMeta = new CompatibilityMetadata(1, "Pam's HarvestCraft 2 Food Core", "pamhc2foodcore", "1.0.4", null);
        CompatibilityPackState vState = new CompatibilityPackState("pamhc2foodcore", vMeta, CompatibilityVerificationStatus.VERIFIED, "1.0.4");
        List<String> vLines = MuslimQolCommands.formatPackDiagnostics(vState);
        assertEquals(4, vLines.size());
        assertEquals("Pam's HarvestCraft 2 Food Core", vLines.get(0));
        assertEquals("  Target: 1.0.4", vLines.get(1));
        assertEquals("  Installed: 1.0.4", vLines.get(2));
        assertEquals("  Status: VERIFIED", vLines.get(3));

        // Unverified pack
        CompatibilityPackState uState = new CompatibilityPackState("pamhc2foodcore", vMeta, CompatibilityVerificationStatus.UNVERIFIED, "1.0.5");
        List<String> uLines = MuslimQolCommands.formatPackDiagnostics(uState);
        assertEquals(4, uLines.size());
        assertEquals("Pam's HarvestCraft 2 Food Core", uLines.get(0));
        assertEquals("  Target: 1.0.4", uLines.get(1));
        assertEquals("  Installed: 1.0.5", uLines.get(2));
        assertEquals("  Status: UNVERIFIED", uLines.get(3));

        // Legacy unversioned pack
        CompatibilityMetadata lMeta = new CompatibilityMetadata(1, "Farmer's Delight", "farmersdelight", null, null);
        CompatibilityPackState lState = new CompatibilityPackState("farmersdelight", lMeta, CompatibilityVerificationStatus.VERIFIED, "1.3.4");
        List<String> lLines = MuslimQolCommands.formatPackDiagnostics(lState);
        assertEquals(4, lLines.size());
        assertEquals("Farmer's Delight", lLines.get(0));
        assertEquals("  Target: unspecified", lLines.get(1));
        assertEquals("  Installed: 1.3.4", lLines.get(2));
        assertEquals("  Status: VERIFIED (legacy unversioned pack)", lLines.get(3));

        // Skipped pack
        CompatibilityPackState sState = new CompatibilityPackState("pamhc2foodcore", vMeta, CompatibilityVerificationStatus.SKIPPED, null);
        List<String> sLines = MuslimQolCommands.formatPackDiagnostics(sState);
        assertEquals(4, sLines.size());
        assertEquals("Pam's HarvestCraft 2 Food Core", sLines.get(0));
        assertEquals("  Target: 1.0.4", sLines.get(1));
        assertEquals("  Installed: missing mod (pamhc2foodcore)", sLines.get(2));
        assertEquals("  Status: SKIPPED", sLines.get(3));
    }

    // ── 7. Pam Future Version Synthetic Regression ──────────────────────────────

    @Test
    void testPamsSyntheticFutureVersionRemainsActiveButUnverified() throws Exception {
        String ns = "muslimqol_pamhc2foodcore";
        String targetMod = "pamhc2foodcore";
        String metaPath = "/data/" + ns + "/muslimqol/compatibility.json";

        CompatibilityMetadata meta;
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(metaPath), StandardCharsets.UTF_8)) {
            meta = ((MetadataParseResult.Valid) CompatibilityMetadata.parse(JsonParser.parseReader(reader).getAsJsonObject())).metadata();
        }

        // Simulate pamhc2foodcore 1.0.5 is loaded
        FoodCompatibilityManager.setModLoadedChecker(targetMod::equals);
        FoodCompatibilityManager.setModVersionResolver(mod -> targetMod.equals(mod) ? Optional.of("1.0.5") : Optional.empty());

        CompatibilityPackState packState = FoodCompatibilityManager.evaluatePack(ns, meta);
        assertTrue(packState.isActive(), "Pack must remain active on unverified version");
        assertTrue(packState.isUnverified(), "Pack must be marked UNVERIFIED");
        assertFalse(packState.isVerified(), "Pack must NOT be marked VERIFIED");
        assertEquals("1.0.5", packState.installedVersion());

        // Load pack classifications and verify that all 180 items still load into runtime
        Map<String, CompatibilityMetadata> activePacks = Map.of(ns, meta);
        Map<String, CompatibilityMetadata> skippedPacks = Map.of();

        Map<ResourceLocation, JsonElement> jsonMap = loadPamsFoodCoreJsonMap(ns);
        Map<ResourceLocation, List<FoodClassification>> parsed =
                FoodClassificationJsonLoader.parseAllMulti(jsonMap, activePacks, skippedPacks);

        assertEquals(180, parsed.size(), "All 180 items still resolve even under UNVERIFIED status");
    }

    // ── 8. Bundled Packs Exact Status Distribution ──────────────────────────────

    @Test
    void testBundledPacksExactStatusDistribution() throws Exception {
        // Pam exact target 1.0.4
        FoodCompatibilityManager.setModLoadedChecker("pamhc2foodcore"::equals);
        FoodCompatibilityManager.setModVersionResolver(mod -> "pamhc2foodcore".equals(mod) ? Optional.of("1.0.4") : Optional.empty());

        CompatibilityMetadata pamMeta;
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/data/muslimqol_pamhc2foodcore/muslimqol/compatibility.json"), StandardCharsets.UTF_8)) {
            pamMeta = ((MetadataParseResult.Valid) CompatibilityMetadata.parse(JsonParser.parseReader(reader).getAsJsonObject())).metadata();
        }
        CompatibilityPackState pamState = FoodCompatibilityManager.evaluatePack("muslimqol_pamhc2foodcore", pamMeta);
        assertTrue(pamState.isActive());
        assertTrue(pamState.isVerified());

        Map<ResourceLocation, JsonElement> pamJsons = loadPamsFoodCoreJsonMap("muslimqol_pamhc2foodcore");
        Map<ResourceLocation, List<FoodClassification>> pamParsed =
                FoodClassificationJsonLoader.parseAllMulti(pamJsons, Map.of("muslimqol_pamhc2foodcore", pamMeta), Map.of());

        assertEquals(180, pamParsed.size());
        Map<FoodStatus, Integer> pamDist = new HashMap<>();
        pamParsed.values().forEach(list -> pamDist.merge(list.get(0).status(), 1, Integer::sum));
        assertEquals(125, pamDist.get(FoodStatus.HALAL));
        assertEquals(13, pamDist.get(FoodStatus.RESTRICTED));
        assertEquals(13, pamDist.get(FoodStatus.DOUBTFUL));
        assertEquals(29, pamDist.get(FoodStatus.UNKNOWN));

        // Farmer's Delight exact target 1.3.4
        FoodCompatibilityManager.setModLoadedChecker("farmersdelight"::equals);
        FoodCompatibilityManager.setModVersionResolver(mod -> "farmersdelight".equals(mod) ? Optional.of("1.3.4") : Optional.empty());

        CompatibilityMetadata fdMeta;
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/data/muslimqol_farmersdelight/muslimqol/compatibility.json"), StandardCharsets.UTF_8)) {
            fdMeta = ((MetadataParseResult.Valid) CompatibilityMetadata.parse(JsonParser.parseReader(reader).getAsJsonObject())).metadata();
        }
        CompatibilityPackState fdState = FoodCompatibilityManager.evaluatePack("muslimqol_farmersdelight", fdMeta);
        assertTrue(fdState.isActive());
        assertTrue(fdState.isVerified());

        Map<ResourceLocation, JsonElement> fdJsons = loadFarmersDelightJsonMap("muslimqol_farmersdelight");
        Map<ResourceLocation, List<FoodClassification>> fdParsed =
                FoodClassificationJsonLoader.parseAllMulti(fdJsons, Map.of("muslimqol_farmersdelight", fdMeta), Map.of());

        assertEquals(89, fdParsed.size());
        Map<FoodStatus, Integer> fdDist = new HashMap<>();
        fdParsed.values().forEach(list -> fdDist.merge(list.get(0).status(), 1, Integer::sum));
        assertEquals(52, fdDist.get(FoodStatus.HALAL));
        assertEquals(11, fdDist.get(FoodStatus.RESTRICTED));
        assertEquals(5, fdDist.get(FoodStatus.DOUBTFUL));
        assertEquals(21, fdDist.get(FoodStatus.UNKNOWN));
    }

    // ── 9. Four-Argument State Constructor Regression ───────────────────────────

    @Test
    void testFourArgumentStateConstructorWithVersionedMetadata() {
        CompatibilityMetadata versionedMeta = new CompatibilityMetadata(
                1,
                "Pam Test",
                "pamhc2foodcore",
                "1.0.4",
                "acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9"
        );
        CompatibilityMetadata legacyMeta = new CompatibilityMetadata(
                1,
                "Legacy Pack",
                "examplemod"
        );
        CompatibilityMetadata skippedMeta = new CompatibilityMetadata(
                1,
                "Skipped Pack",
                "absentmod",
                "2.0.0",
                null
        );

        ClassificationRuntimeState state = new ClassificationRuntimeState(
                Map.of(),
                Map.of(),
                Map.of("pam", versionedMeta, "legacy", legacyMeta),
                Map.of("skipped", skippedMeta)
        );

        // 1. Versioned active pack without installed-version evidence must be UNVERIFIED, installedVersion=null
        assertTrue(state.packStates().containsKey("pam"));
        CompatibilityPackState pamState = state.packStates().get("pam");
        assertTrue(pamState.isActive());
        assertEquals(CompatibilityVerificationStatus.UNVERIFIED, pamState.verificationStatus());
        assertNull(pamState.installedVersion(), "installedVersion must be null, not fabricated from targetVersion");
        assertFalse(pamState.isVerified());
        assertTrue(pamState.isUnverified());
        assertFalse(state.verifiedPacks().containsKey("pam"), "verifiedPacks must NOT contain unevaluated versioned pack");
        assertTrue(state.unverifiedPacks().containsKey("pam"), "unverifiedPacks must contain unevaluated versioned pack");

        // 2. Legacy unversioned pack must be VERIFIED (legacy unversioned pack), installedVersion=null
        assertTrue(state.packStates().containsKey("legacy"));
        CompatibilityPackState legacyState = state.packStates().get("legacy");
        assertTrue(legacyState.isActive());
        assertEquals(CompatibilityVerificationStatus.VERIFIED, legacyState.verificationStatus());
        assertNull(legacyState.installedVersion());
        assertTrue(legacyState.isVerified());
        assertFalse(legacyState.isUnverified());
        assertTrue(state.verifiedPacks().containsKey("legacy"));
        assertFalse(state.unverifiedPacks().containsKey("legacy"));

        // 3. Skipped pack must be SKIPPED, installedVersion=null
        assertTrue(state.packStates().containsKey("skipped"));
        CompatibilityPackState skipState = state.packStates().get("skipped");
        assertFalse(skipState.isActive());
        assertTrue(skipState.isSkipped());
        assertEquals(CompatibilityVerificationStatus.SKIPPED, skipState.verificationStatus());
        assertNull(skipState.installedVersion());
        assertFalse(state.verifiedPacks().containsKey("skipped"));
        assertFalse(state.unverifiedPacks().containsKey("skipped"));
    }

    // ── 10. CompatibilitySnapshot Legacy Constructor Regression ─────────────────

    @Test
    void testCompatibilitySnapshotLegacyConstructorWithVersionedMetadata() {
        CompatibilityMetadata versionedMeta = new CompatibilityMetadata(
                1,
                "Pam Test",
                "pamhc2foodcore",
                "1.0.4",
                "acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9"
        );

        // Legacy 3-argument snapshot constructor: (providers, activePacks, skippedPacks)
        CompatibilitySnapshot snapshot = new CompatibilitySnapshot(
                List.of(),
                Map.of("pam", versionedMeta),
                Map.of()
        );

        assertTrue(snapshot.getPackStates().containsKey("pam"));
        CompatibilityPackState packState = snapshot.getPackStates().get("pam");

        // Must be UNVERIFIED with null installedVersion
        assertEquals(CompatibilityVerificationStatus.UNVERIFIED, packState.verificationStatus());
        assertNull(packState.installedVersion(), "installedVersion must be null, never fabricated 1.0.4");
        assertFalse(packState.isVerified());
        assertTrue(packState.isUnverified());

        // Diagnostic output must show Installed: unknown, Status: UNVERIFIED
        List<String> lines = MuslimQolCommands.formatCompatibilityDiagnostics(snapshot);
        assertTrue(lines.contains("Pam Test"));
        assertTrue(lines.contains("  Target: 1.0.4"));
        assertTrue(lines.contains("  Installed: unknown"));
        assertTrue(lines.contains("  Status: UNVERIFIED"));
        assertFalse(lines.contains("  Status: VERIFIED"));
        assertFalse(lines.contains("  Installed: 1.0.4"), "Installed must not claim 1.0.4 without resolution");
    }

    // ── 11. Explicit Evaluated State vs Unevaluated State Distinction ────────────

    @Test
    void testExplicitEvaluatedPackStateVsUnevaluatedConstruction() {
        CompatibilityMetadata meta = new CompatibilityMetadata(
                1,
                "Pam Food Core",
                "pamhc2foodcore",
                "1.0.4",
                "acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9"
        );

        // 1. Explicit evaluated path with resolver returning "1.0.4"
        FoodCompatibilityManager.setModLoadedChecker("pamhc2foodcore"::equals);
        FoodCompatibilityManager.setModVersionResolver(mod -> "pamhc2foodcore".equals(mod) ? Optional.of("1.0.4") : Optional.empty());

        CompatibilityPackState evaluatedState = FoodCompatibilityManager.evaluatePack("pamhc2foodcore", meta);
        assertEquals(CompatibilityVerificationStatus.VERIFIED, evaluatedState.verificationStatus());
        assertEquals("1.0.4", evaluatedState.installedVersion());

        List<String> evalLines = MuslimQolCommands.formatPackDiagnostics(evaluatedState);
        assertTrue(evalLines.contains("  Target: 1.0.4"));
        assertTrue(evalLines.contains("  Installed: 1.0.4"));
        assertTrue(evalLines.contains("  Status: VERIFIED"));

        // 2. In contrast, unevaluated 4-arg runtime state constructor with the exact same metadata
        ClassificationRuntimeState state = new ClassificationRuntimeState(
                Map.of(), Map.of(), Map.of("pamhc2foodcore", meta), Map.of()
        );
        CompatibilityPackState unevaluatedState = state.packStates().get("pamhc2foodcore");
        assertEquals(CompatibilityVerificationStatus.UNVERIFIED, unevaluatedState.verificationStatus());
        assertNull(unevaluatedState.installedVersion());

        List<String> unevalLines = MuslimQolCommands.formatPackDiagnostics(unevaluatedState);
        assertTrue(unevalLines.contains("  Target: 1.0.4"));
        assertTrue(unevalLines.contains("  Installed: unknown"));
        assertTrue(unevalLines.contains("  Status: UNVERIFIED"));
    }

    private Map<ResourceLocation, JsonElement> loadPamsFoodCoreJsonMap(String namespace) throws Exception {
        Map<ResourceLocation, JsonElement> jsonMap = new HashMap<>();
        for (String file : List.of("pork.json", "doubtful.json", "meat_unknown.json", "fish.json", "plants.json", "prepared_meals.json")) {
            String path = "/data/" + namespace + "/muslimqol/food_classifications/" + file;
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(path), StandardCharsets.UTF_8)) {
                jsonMap.put(ResourceLocation.fromNamespaceAndPath(namespace, file), JsonParser.parseReader(reader).getAsJsonObject());
            }
        }
        return jsonMap;
    }

    private Map<ResourceLocation, JsonElement> loadFarmersDelightJsonMap(String namespace) throws Exception {
        Map<ResourceLocation, JsonElement> jsonMap = new HashMap<>();
        List<String> files = List.of(
                "pork.json", "carrion.json", "meat_unknown.json", "seafood_unknown.json",
                "doubtful.json", "fish.json", "plants.json", "prepared_meals.json",
                "desserts.json", "drinks.json"
        );
        for (String file : files) {
            String path = "/data/" + namespace + "/muslimqol/food_classifications/" + file;
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(path), StandardCharsets.UTF_8)) {
                jsonMap.put(ResourceLocation.fromNamespaceAndPath(namespace, file), JsonParser.parseReader(reader).getAsJsonObject());
            }
        }
        return jsonMap;
    }
}
