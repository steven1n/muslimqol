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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe registry holding runtime classifications from Datapacks and User Overrides.
 * <p>
 * Employs immutable snapshot maps held in {@link AtomicReference} to guarantee that concurrent
 * readers always observe a complete, consistent state—never a cleared or partially updated map.
 */
public final class FoodClassificationRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodClassificationRegistry.class);

    private static final AtomicReference<Map<ResourceLocation, List<FoodClassification>>> DATAPACK_ENTRIES =
            new AtomicReference<>(Map.of());
    private static final AtomicReference<Map<ResourceLocation, FoodClassification>> USER_OVERRIDES =
            new AtomicReference<>(Map.of());

    private FoodClassificationRegistry() {}

    /**
     * Atomically replaces the datapack classifications map from single-entry mappings (v0.1 backward-compatible).
     */
    public static void setDatapackClassifications(Map<ResourceLocation, FoodClassification> entries) {
        if (entries == null || entries.isEmpty()) {
            DATAPACK_ENTRIES.set(Map.of());
        } else {
            Map<ResourceLocation, List<FoodClassification>> map = new HashMap<>();
            entries.forEach((id, classification) -> {
                if (id != null && classification != null) {
                    map.put(id, List.of(classification));
                }
            });
            DATAPACK_ENTRIES.set(Collections.unmodifiableMap(map));
        }
        LOGGER.info("Loaded {} datapack food classifications (atomic swap)", entries != null ? entries.size() : 0);
        FoodCompatibilityManager.rebuildSnapshot();
    }

    /**
     * Atomically replaces the datapack classifications map preserving multiple candidates per item.
     */
    public static void setDatapackMultiClassifications(Map<ResourceLocation, List<FoodClassification>> entries) {
        if (entries == null || entries.isEmpty()) {
            DATAPACK_ENTRIES.set(Map.of());
        } else {
            Map<ResourceLocation, List<FoodClassification>> map = new HashMap<>();
            entries.forEach((id, list) -> {
                if (id != null && list != null && !list.isEmpty()) {
                    List<FoodClassification> sorted = new ArrayList<>(list);
                    sorted.sort(Comparator.comparing(c -> c.providerId().toString()));
                    map.put(id, Collections.unmodifiableList(sorted));
                }
            });
            DATAPACK_ENTRIES.set(Collections.unmodifiableMap(map));
        }
        LOGGER.info("Loaded {} datapack food classification keys (atomic multi-swap)", entries != null ? entries.size() : 0);
        FoodCompatibilityManager.rebuildSnapshot();
    }

    /**
     * Atomically registers a single datapack entry, appending it to the candidates list for this item.
     */
    public static void registerDatapackEntry(ResourceLocation id, FoodClassification classification) {
        if (id == null || classification == null) return;
        DATAPACK_ENTRIES.updateAndGet(oldMap -> {
            Map<ResourceLocation, List<FoodClassification>> updated = new HashMap<>(oldMap);
            List<FoodClassification> list = new ArrayList<>(updated.getOrDefault(id, List.of()));
            list.add(classification);
            list.sort(Comparator.comparing(c -> c.providerId().toString()));
            updated.put(id, Collections.unmodifiableList(list));
            return Collections.unmodifiableMap(updated);
        });
        FoodCompatibilityManager.rebuildSnapshot();
    }

    /**
     * Returns the primary (deterministic first) datapack classification for an item.
     */
    public static Optional<FoodClassification> getDatapackClassification(ResourceLocation id) {
        if (id == null) return Optional.empty();
        List<FoodClassification> list = DATAPACK_ENTRIES.get().get(id);
        if (list != null && !list.isEmpty()) {
            return Optional.of(list.get(0));
        }
        return Optional.empty();
    }

    /**
     * Returns all candidate datapack classifications contributed for an item.
     */
    public static List<FoodClassification> getDatapackClassifications(ResourceLocation id) {
        if (id == null) return List.of();
        List<FoodClassification> list = DATAPACK_ENTRIES.get().get(id);
        return list != null ? list : List.of();
    }

    /**
     * Atomically registers a user override.
     */
    public static void registerUserOverride(ResourceLocation id, FoodClassification classification) {
        if (id == null || classification == null) return;
        USER_OVERRIDES.updateAndGet(oldMap -> {
            Map<ResourceLocation, FoodClassification> updated = new HashMap<>(oldMap);
            updated.put(id, classification);
            return Collections.unmodifiableMap(updated);
        });
        FoodCompatibilityManager.rebuildSnapshot();
    }

    public static Optional<FoodClassification> getUserOverride(ResourceLocation id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(USER_OVERRIDES.get().get(id));
    }

    public static void clearDatapack() {
        DATAPACK_ENTRIES.set(Map.of());
        FoodCompatibilityManager.rebuildSnapshot();
    }

    public static void clearUserOverrides() {
        USER_OVERRIDES.set(Map.of());
        FoodCompatibilityManager.rebuildSnapshot();
    }

    public static void clearAll() {
        DATAPACK_ENTRIES.set(Map.of());
        USER_OVERRIDES.set(Map.of());
        FoodCompatibilityManager.resetToDefaults();
    }

    /**
     * Returns an unmodifiable snapshot view of primary datapack entries (v0.1 backward-compatible).
     */
    public static Map<ResourceLocation, FoodClassification> getDatapackEntries() {
        Map<ResourceLocation, List<FoodClassification>> raw = DATAPACK_ENTRIES.get();
        Map<ResourceLocation, FoodClassification> result = new HashMap<>();
        raw.forEach((k, v) -> {
            if (v != null && !v.isEmpty()) {
                result.put(k, v.get(0));
            }
        });
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns the complete unmodifiable multi-candidate datapack entries map.
     */
    public static Map<ResourceLocation, List<FoodClassification>> getDatapackMultiEntries() {
        return DATAPACK_ENTRIES.get();
    }

    public static Map<ResourceLocation, FoodClassification> getUserOverrides() {
        return USER_OVERRIDES.get();
    }

    /**
     * Parses and atomically updates user overrides from the CommonConfig specification.
     */
    public static void reloadUserOverridesFromConfig() {
        Map<ResourceLocation, FoodClassification> tempMap = new HashMap<>();
        try {
            if (CommonConfig.SPEC != null && CommonConfig.SPEC.isLoaded()) {
                List<? extends String> rawList = CommonConfig.USER_OVERRIDES.get();
                if (rawList != null) {
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
                            tempMap.put(locOpt.get(), new FoodClassification(
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
                }
            }
            // Single atomic swap: readers never observe an empty or half-loaded map
            USER_OVERRIDES.set(Collections.unmodifiableMap(tempMap));
            LOGGER.info("Loaded {} user food override classifications (atomic swap)", tempMap.size());
        } catch (Exception e) {
            LOGGER.error("Failed to reload user food overrides from configuration", e);
        } finally {
            FoodCompatibilityManager.rebuildSnapshot();
        }
    }
}
