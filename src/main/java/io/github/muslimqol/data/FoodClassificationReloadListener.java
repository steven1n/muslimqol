package io.github.muslimqol.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.compat.ClassificationRuntimeState;
import io.github.muslimqol.compat.CompatibilityMetadata;
import io.github.muslimqol.compat.CompatibilityPackState;
import io.github.muslimqol.compat.CompatibilityVerificationStatus;
import io.github.muslimqol.compat.FoodCompatibilityManager;
import io.github.muslimqol.compat.MetadataParseResult;
import io.github.muslimqol.food.FoodClassificationRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reload listener loading food classification JSON data from datapacks under `data/<namespace>/muslimqol/food_classifications/`
 * with optional compatibility pack metadata under `data/<namespace>/muslimqol/compatibility.json`.
 */
public class FoodClassificationReloadListener extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodClassificationReloadListener.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public FoodClassificationReloadListener() {
        super(GSON, "muslimqol/food_classifications");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonMap, ResourceManager resourceManager, ProfilerFiller profiler) {
        LOGGER.info("Applying food classifications from datapacks...");

        Map<String, CompatibilityMetadata> activePacks = new HashMap<>();
        Map<String, CompatibilityMetadata> skippedPacks = new HashMap<>();
        Map<String, CompatibilityPackState> packStates = new HashMap<>();

        if (jsonMap != null && resourceManager != null) {
            Set<String> namespaces = new HashSet<>();
            for (ResourceLocation id : jsonMap.keySet()) {
                namespaces.add(id.getNamespace());
            }

            for (String namespace : namespaces) {
                ResourceLocation metaLoc = ResourceLocation.fromNamespaceAndPath(namespace, "muslimqol/compatibility.json");
                Optional<Resource> res = resourceManager.getResource(metaLoc);
                MetadataParseResult parseResult;
                if (res.isPresent()) {
                    try (var reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                        JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
                        if (parsed != null && parsed.isJsonObject()) {
                            parseResult = CompatibilityMetadata.parse(parsed.getAsJsonObject());
                        } else {
                            parseResult = MetadataParseResult.invalid("Root element is not a JSON object");
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to parse compatibility metadata from {}: {}", metaLoc, e.getMessage());
                        parseResult = MetadataParseResult.invalid("Malformed JSON: " + e.getMessage());
                    }
                } else {
                    parseResult = MetadataParseResult.absent();
                }

                if (parseResult instanceof MetadataParseResult.Valid valid) {
                    CompatibilityMetadata meta = valid.metadata();
                    CompatibilityPackState packState = FoodCompatibilityManager.evaluatePack(namespace, meta);
                    packStates.put(namespace, packState);

                    if (packState.isSkipped()) {
                        skippedPacks.put(namespace, meta);
                        LOGGER.info("Skipped compatibility pack '{}' ({}) because target mod '{}' is not loaded",
                                meta.name(), namespace, meta.targetMod());
                    } else if (packState.isVerified()) {
                        activePacks.put(namespace, meta);
                        if (meta.targetVersion() != null) {
                            LOGGER.info("Compatibility pack '{}' verified for {} {}.",
                                    meta.name(), meta.targetMod(), packState.installedVersion());
                        } else {
                            LOGGER.info("Compatibility pack '{}' ({}) loaded (legacy unversioned pack).",
                                    meta.name(), namespace);
                        }
                    } else {
                        activePacks.put(namespace, meta);
                        String installedDesc = packState.installedVersion() != null ? packState.installedVersion() : "unavailable";
                        LOGGER.warn("Compatibility pack '{}' was audited for {} {}, but installed version is {}. The pack remains active but is UNVERIFIED for this version.",
                                meta.name(), meta.targetMod(), meta.targetVersion(), installedDesc);
                    }
                } else if (parseResult instanceof MetadataParseResult.Invalid invalid) {
                    LOGGER.warn("Skipping compatibility classifications for namespace '{}' due to invalid metadata: {}",
                            namespace, invalid.reason());
                    CompatibilityMetadata invalidMeta = new CompatibilityMetadata(-1, "Invalid Metadata (" + invalid.reason() + ")", null);
                    skippedPacks.put(namespace, invalidMeta);
                    packStates.put(namespace, new CompatibilityPackState(namespace, invalidMeta, CompatibilityVerificationStatus.SKIPPED, null));
                }
                // MetadataParseResult.Absent loads normally without entry in active or skipped packs
            }
        }

        // Parse datapacks using active and skipped packs
        Map<ResourceLocation, List<FoodClassification>> datapackEntries =
                FoodClassificationJsonLoader.parseAllMulti(jsonMap, activePacks, skippedPacks);

        // Parse user overrides from config
        Map<ResourceLocation, FoodClassification> userOverrides =
                FoodClassificationRegistry.parseUserOverridesFromConfig();

        // Build COMPLETE immutable runtime state
        ClassificationRuntimeState runtimeState = new ClassificationRuntimeState(
                datapackEntries,
                userOverrides,
                activePacks,
                skippedPacks,
                packStates
        );

        // Single atomic swap: readers see generation N or generation N+1, never a mixture
        FoodClassificationRegistry.applyRuntimeState(runtimeState);
        LOGGER.info("Applied {} datapack classification keys and {} user overrides transactionally",
                datapackEntries.size(), userOverrides.size());
    }
}
