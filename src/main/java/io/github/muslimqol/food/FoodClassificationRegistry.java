package io.github.muslimqol.food;

import io.github.muslimqol.api.ClassificationPriority;
import io.github.muslimqol.api.ClassificationProviderId;
import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.compat.FoodCompatibilityManager;
import io.github.muslimqol.config.CommonConfig;
import io.github.muslimqol.util.ResourceLocationUtil;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding runtime classifications from Datapacks and User Overrides.
 */
public final class FoodClassificationRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodClassificationRegistry.class);

    private static final Map<ResourceLocation, FoodClassification> DATAPACK_ENTRIES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, FoodClassification> USER_OVERRIDES = new ConcurrentHashMap<>();

    private FoodClassificationRegistry() {}

    public static void setDatapackClassifications(Map<ResourceLocation, FoodClassification> entries) {
        DATAPACK_ENTRIES.clear();
        if (entries != null) {
            DATAPACK_ENTRIES.putAll(entries);
        }
        LOGGER.info("Loaded {} datapack food classifications", DATAPACK_ENTRIES.size());
        FoodCompatibilityManager.rebuildSnapshot();
    }

    public static void registerDatapackEntry(ResourceLocation id, FoodClassification classification) {
        DATAPACK_ENTRIES.put(id, classification);
        FoodCompatibilityManager.rebuildSnapshot();
    }

    public static Optional<FoodClassification> getDatapackClassification(ResourceLocation id) {
        return Optional.ofNullable(DATAPACK_ENTRIES.get(id));
    }

    public static void registerUserOverride(ResourceLocation id, FoodClassification classification) {
        USER_OVERRIDES.put(id, classification);
        FoodCompatibilityManager.rebuildSnapshot();
    }

    public static Optional<FoodClassification> getUserOverride(ResourceLocation id) {
        return Optional.ofNullable(USER_OVERRIDES.get(id));
    }

    public static void clearDatapack() {
        DATAPACK_ENTRIES.clear();
        FoodCompatibilityManager.rebuildSnapshot();
    }

    public static void clearUserOverrides() {
        USER_OVERRIDES.clear();
        FoodCompatibilityManager.rebuildSnapshot();
    }

    public static void clearAll() {
        DATAPACK_ENTRIES.clear();
        USER_OVERRIDES.clear();
        FoodCompatibilityManager.resetToDefaults();
    }

    public static Map<ResourceLocation, FoodClassification> getDatapackEntries() {
        return Collections.unmodifiableMap(new HashMap<>(DATAPACK_ENTRIES));
    }

    public static Map<ResourceLocation, FoodClassification> getUserOverrides() {
        return Collections.unmodifiableMap(new HashMap<>(USER_OVERRIDES));
    }

    /**
     * Parses and updates user overrides from the CommonConfig specification.
     */
    public static void reloadUserOverridesFromConfig() {
        USER_OVERRIDES.clear();
        try {
            if (!CommonConfig.SPEC.isLoaded()) {
                FoodCompatibilityManager.rebuildSnapshot();
                return;
            }
            List<? extends String> rawList = CommonConfig.USER_OVERRIDES.get();
            if (rawList == null) {
                FoodCompatibilityManager.rebuildSnapshot();
                return;
            }
            for (String line : rawList) {
                if (line == null || line.isBlank()) continue;
                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;

                var locOpt = ResourceLocationUtil.tryParse(parts[0].trim());
                if (locOpt.isEmpty()) continue;

                String val = parts[1].trim();
                String statusStr = val;
                String reason = "user_override";

                if (val.contains(":")) {
                    String[] sub = val.split(":", 2);
                    statusStr = sub[0].trim();
                    reason = sub[1].trim();
                }

                try {
                    FoodStatus status = FoodStatus.valueOf(statusStr.toUpperCase());
                    USER_OVERRIDES.put(locOpt.get(), new FoodClassification(
                            status,
                            reason,
                            ClassificationSource.USER_OVERRIDE,
                            ClassificationProviderId.USER_OVERRIDE,
                            ClassificationPriority.USER_OVERRIDE
                    ));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Invalid FoodStatus '{}' in user override line: {}", statusStr, line);
                }
            }
            LOGGER.info("Loaded {} user food override classifications", USER_OVERRIDES.size());
        } catch (Exception e) {
            LOGGER.error("Failed to reload user food overrides from configuration", e);
        } finally {
            FoodCompatibilityManager.rebuildSnapshot();
        }
    }
}
