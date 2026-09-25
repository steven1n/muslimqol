package io.github.muslimqol.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.muslimqol.api.ClassificationPriority;
import io.github.muslimqol.api.ClassificationProviderId;
import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.compat.CompatibilityMetadata;
import io.github.muslimqol.util.ResourceLocationUtil;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses JSON configurations into FoodClassification mappings with support for
 * namespace-based compatibility pack filtering and multi-candidate conflict preservation.
 */
public final class FoodClassificationJsonLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodClassificationJsonLoader.class);

    private FoodClassificationJsonLoader() {}

    /**
     * Legacy parseAll for v0.1 compatibility returning primary single-entry mappings.
     */
    public static Map<ResourceLocation, FoodClassification> parseAll(Map<ResourceLocation, JsonElement> jsonMap) {
        return parseAll(jsonMap, Map.of(), Map.of());
    }

    /**
     * Parses all json entries into single-entry mappings (v0.1 backward-compatible).
     */
    public static Map<ResourceLocation, FoodClassification> parseAll(
            Map<ResourceLocation, JsonElement> jsonMap,
            Map<String, CompatibilityMetadata> activePacks,
            Map<String, CompatibilityMetadata> skippedPacks
    ) {
        Map<ResourceLocation, List<FoodClassification>> multi = parseAllMulti(jsonMap, activePacks, skippedPacks);
        Map<ResourceLocation, FoodClassification> result = new HashMap<>();
        multi.forEach((id, list) -> {
            if (list != null && !list.isEmpty()) {
                result.put(id, list.get(0));
            }
        });
        return result;
    }

    /**
     * Parses all json entries preserving all candidate classifications per item across multiple datapacks.
     */
    public static Map<ResourceLocation, List<FoodClassification>> parseAllMulti(Map<ResourceLocation, JsonElement> jsonMap) {
        return parseAllMulti(jsonMap, Map.of(), Map.of());
    }

    /**
     * Parses all json entries into multi-candidate mappings, skipping namespaces whose target mods are not loaded.
     */
    public static Map<ResourceLocation, List<FoodClassification>> parseAllMulti(
            Map<ResourceLocation, JsonElement> jsonMap,
            Map<String, CompatibilityMetadata> activePacks,
            Map<String, CompatibilityMetadata> skippedPacks
    ) {
        Map<ResourceLocation, List<FoodClassification>> result = new HashMap<>();

        if (jsonMap == null) {
            return result;
        }

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsonMap.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String namespace = fileId.getNamespace();

            // Skip namespaces identified as skipped due to missing target mods
            if (skippedPacks != null && skippedPacks.containsKey(namespace)) {
                continue;
            }

            JsonElement element = entry.getValue();

            if (!element.isJsonObject()) {
                LOGGER.warn("Skipping non-object JSON food classification: {}", fileId);
                continue;
            }

            JsonObject obj = element.getAsJsonObject();
            parseEntry(fileId, obj, result);
        }

        // Sort candidates deterministically by providerId
        Map<ResourceLocation, List<FoodClassification>> sortedResult = new HashMap<>();
        result.forEach((id, list) -> {
            List<FoodClassification> copy = new ArrayList<>(list);
            copy.sort(Comparator.comparing(c -> c.providerId().toString()));
            sortedResult.put(id, Collections.unmodifiableList(copy));
        });

        return Collections.unmodifiableMap(sortedResult);
    }

    private static void parseEntry(ResourceLocation fileId, JsonObject obj, Map<ResourceLocation, List<FoodClassification>> result) {
        // Multi-entry array: "values": [ { ... }, { ... } ]
        if (obj.has("values") && obj.get("values").isJsonArray()) {
            JsonArray array = obj.getAsJsonArray("values");
            for (JsonElement itemElem : array) {
                if (itemElem.isJsonObject()) {
                    parseSingleObject(fileId, itemElem.getAsJsonObject(), result);
                }
            }
            return;
        }

        // Multi-entry map: "entries": { "item_id": { ... } }
        if (obj.has("entries") && obj.get("entries").isJsonObject()) {
            JsonObject entriesObj = obj.getAsJsonObject("entries");
            for (Map.Entry<String, JsonElement> entry : entriesObj.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    var loc = ResourceLocationUtil.tryParse(entry.getKey());
                    loc.ifPresent(resourceLocation -> parseSingleObject(resourceLocation, entry.getValue().getAsJsonObject(), result));
                }
            }
            return;
        }

        // Single entry directly in root object
        parseSingleObject(fileId, obj, result);
    }

    private static void parseSingleObject(ResourceLocation fallbackId, JsonObject obj, Map<ResourceLocation, List<FoodClassification>> result) {
        ResourceLocation targetId = fallbackId;
        if (obj.has("item")) {
            String itemStr = obj.get("item").getAsString();
            var locOpt = ResourceLocationUtil.tryParse(itemStr);
            if (locOpt.isPresent()) {
                targetId = locOpt.get();
            }
        }

        if (targetId == null) {
            LOGGER.warn("Missing 'item' field in food classification object: {}", obj);
            return;
        }

        if (!obj.has("status")) {
            LOGGER.warn("Missing 'status' field in food classification for {}: {}", targetId, obj);
            return;
        }

        String statusStr = obj.get("status").getAsString().toUpperCase();
        FoodStatus status;
        try {
            status = FoodStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Unknown FoodStatus '{}' for {}", statusStr, targetId);
            return;
        }

        String reason = obj.has("reason") ? obj.get("reason").getAsString() : "datapack";
        ClassificationProviderId providerId = fallbackId != null
                ? ClassificationProviderId.of(fallbackId.getNamespace(), "datapack")
                : ClassificationProviderId.DATAPACK;

        FoodClassification classification = new FoodClassification(
                status,
                reason,
                ClassificationSource.DATAPACK,
                providerId,
                ClassificationPriority.DATAPACK
        );

        result.computeIfAbsent(targetId, k -> new ArrayList<>()).add(classification);
    }
}
