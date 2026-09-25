package io.github.muslimqol.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.util.ResourceLocationUtil;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses JSON configurations into FoodClassification mappings.
 */
public final class FoodClassificationJsonLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodClassificationJsonLoader.class);

    private FoodClassificationJsonLoader() {}

    public static Map<ResourceLocation, FoodClassification> parseAll(Map<ResourceLocation, JsonElement> jsonMap) {
        Map<ResourceLocation, FoodClassification> result = new HashMap<>();

        if (jsonMap == null) {
            return result;
        }

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsonMap.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonElement element = entry.getValue();

            if (!element.isJsonObject()) {
                LOGGER.warn("Skipping non-object JSON food classification: {}", fileId);
                continue;
            }

            JsonObject obj = element.getAsJsonObject();
            parseEntry(fileId, obj, result);
        }

        return result;
    }

    private static void parseEntry(ResourceLocation fileId, JsonObject obj, Map<ResourceLocation, FoodClassification> result) {
        // Multi-entry array: "values": [ { ... }, { ... } ]
        if (obj.has("values") && obj.get("values").isJsonArray()) {
            JsonArray array = obj.getAsJsonArray("values");
            for (JsonElement itemElem : array) {
                if (itemElem.isJsonObject()) {
                    parseSingleObject(null, itemElem.getAsJsonObject(), result);
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

    private static void parseSingleObject(ResourceLocation fallbackId, JsonObject obj, Map<ResourceLocation, FoodClassification> result) {
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

        result.put(targetId, new FoodClassification(status, reason, ClassificationSource.DATAPACK));
    }
}
