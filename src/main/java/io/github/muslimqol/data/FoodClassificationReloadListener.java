package io.github.muslimqol.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.compat.CompatibilityMetadata;
import io.github.muslimqol.compat.FoodCompatibilityManager;
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

        if (jsonMap != null && resourceManager != null) {
            Set<String> namespaces = new HashSet<>();
            for (ResourceLocation id : jsonMap.keySet()) {
                namespaces.add(id.getNamespace());
            }

            for (String namespace : namespaces) {
                ResourceLocation metaLoc = ResourceLocation.fromNamespaceAndPath(namespace, "muslimqol/compatibility.json");
                Optional<Resource> res = resourceManager.getResource(metaLoc);
                if (res.isPresent()) {
                    try (var reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                        JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                        Optional<CompatibilityMetadata> metaOpt = CompatibilityMetadata.fromJson(obj);
                        if (metaOpt.isPresent()) {
                            CompatibilityMetadata meta = metaOpt.get();
                            if (meta.targetMod() != null && !FoodCompatibilityManager.isModLoaded(meta.targetMod())) {
                                skippedPacks.put(namespace, meta);
                                LOGGER.info("Skipped compatibility pack '{}' ({}) because target mod '{}' is not loaded",
                                        meta.name(), namespace, meta.targetMod());
                            } else {
                                activePacks.put(namespace, meta);
                                LOGGER.info("Loaded compatibility pack '{}' ({})", meta.name(), namespace);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to parse compatibility metadata from {}: {}", metaLoc, e.getMessage());
                    }
                }
            }
        }

        FoodCompatibilityManager.updateCompatibilityPacks(activePacks, skippedPacks);
        Map<ResourceLocation, FoodClassification> parsed = FoodClassificationJsonLoader.parseAll(jsonMap, activePacks, skippedPacks);
        FoodClassificationRegistry.setDatapackClassifications(parsed);
        FoodClassificationRegistry.reloadUserOverridesFromConfig();
    }
}
