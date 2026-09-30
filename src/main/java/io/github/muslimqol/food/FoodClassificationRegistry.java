package io.github.muslimqol.food;

import io.github.muslimqol.api.ClassificationPriority;
import io.github.muslimqol.api.ClassificationProviderId;
import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.compat.ClassificationRuntimeState;
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
 * Registry maintaining active food classification state across datapacks and user overrides.
 * Backed by an atomic reference to an immutable {@link ClassificationRuntimeState} generation.
 */
public final class FoodClassificationRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodClassificationRegistry.class);

    private static final AtomicReference<ClassificationRuntimeState> RUNTIME_STATE =
            new AtomicReference<>(ClassificationRuntimeState.EMPTY);

    private static final AtomicReference<ClientSyncedClassificationState> CLIENT_SYNCED_STATE =
            new AtomicReference<>(null);

    private static final Comparator<FoodClassification> RULE_COMPARATOR = (a, b) -> {
        int p = Integer.compare(b.priority().level(), a.priority().level());
        if (p != 0) return p;
        int prov = a.providerId().compareTo(b.providerId());
        if (prov != 0) return prov;
        if (a.ruleId() != null && b.ruleId() != null) {
            int r = a.ruleId().compareTo(b.ruleId());
            if (r != 0) return r;
        } else if (a.ruleId() != null) {
            return -1;
        } else if (b.ruleId() != null) {
            return 1;
        }
        int s = a.status().name().compareTo(b.status().name());
        if (s != 0) return s;
        return a.reason().compareTo(b.reason());
    };

    private FoodClassificationRegistry() {}

    /**
     * Returns the currently active runtime state generation.
     */
    public static ClassificationRuntimeState getRuntimeState() {
        return RUNTIME_STATE.get();
    }

    /**
     * Returns the active client-synced classification state from a remote dedicated server, or {@code null} if inactive.
     * <p>
     * Always returns {@code null} on a dedicated server or when a local/integrated {@code MinecraftServer}
     * is active, ensuring server execution paths never read {@link #CLIENT_SYNCED_STATE}.
     */
    public static ClientSyncedClassificationState getClientSyncedState() {
        if (isLogicalServerEnvironment()) {
            return null;
        }
        return CLIENT_SYNCED_STATE.get();
    }

    /**
     * Returns {@code true} when remote dedicated-server classification sync state is active on this client.
     */
    public static boolean hasClientSyncedState() {
        return getClientSyncedState() != null;
    }

    private static boolean isLogicalServerEnvironment() {
        try {
            if (net.neoforged.fml.loading.FMLEnvironment.dist != null
                    && net.neoforged.fml.loading.FMLEnvironment.dist.isDedicatedServer()) {
                return true;
            }
            return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Atomically applies remote server-synced classifications and consumption policies on the client.
     * <p>
     * Does NOT modify {@link #RUNTIME_STATE}, does NOT touch compatibility pack metadata
     * ({@code activePacks}, {@code skippedPacks}, {@code packStates}), and does NOT invoke
     * {@link FoodCompatibilityManager#applyRuntimeState(ClassificationRuntimeState)}.
     */
    public static void applyClientSyncedState(ClientSyncedClassificationState syncedState) {
        if (syncedState == null) {
            clearClientSyncedState();
            return;
        }
        Map<ResourceLocation, List<FoodClassification>> sortedDatapack = new HashMap<>();
        syncedState.datapackEntries().forEach((id, list) -> {
            if (id != null && list != null && !list.isEmpty()) {
                List<FoodClassification> sorted = new ArrayList<>(list);
                sorted.sort(RULE_COMPARATOR);
                sortedDatapack.put(id, Collections.unmodifiableList(sorted));
            }
        });
        ClientSyncedClassificationState normalized = new ClientSyncedClassificationState(
                sortedDatapack,
                syncedState.userOverrides(),
                syncedState.halalPolicy(),
                syncedState.restrictedPolicy(),
                syncedState.doubtfulPolicy(),
                syncedState.unknownPolicy()
        );
        CLIENT_SYNCED_STATE.set(normalized);
        LOGGER.info("Applied remote server classification sync: {} datapack keys, {} user overrides",
                normalized.datapackEntries().size(), normalized.userOverrides().size());
    }

    /**
     * Clears remote server-synced classification state on disconnect without touching local/integrated server state.
     */
    public static void clearClientSyncedState() {
        ClientSyncedClassificationState prev = CLIENT_SYNCED_STATE.getAndSet(null);
        if (prev != null) {
            LOGGER.info("Cleared remote server classification sync state on disconnect");
        }
    }

    /**
     * Atomically swaps the active runtime state generation and updates the compatibility snapshot.
     */
    public static void applyRuntimeState(ClassificationRuntimeState state) {
        ClassificationRuntimeState nonNull = state != null ? state : ClassificationRuntimeState.EMPTY;
        RUNTIME_STATE.set(nonNull);
        FoodCompatibilityManager.applyRuntimeState(nonNull);
    }

    /**
     * Atomically replaces the datapack classifications map from single-entry mappings (v0.1 backward-compatible).
     */
    public static void setDatapackClassifications(Map<ResourceLocation, FoodClassification> entries) {
        Map<ResourceLocation, List<FoodClassification>> multi = new HashMap<>();
        if (entries != null) {
            entries.forEach((id, classification) -> {
                if (id != null && classification != null) {
                    multi.put(id, List.of(classification));
                }
            });
        }
        setDatapackMultiClassifications(multi);
    }

    /**
     * Atomically replaces the datapack classifications map preserving multiple candidates per item.
     */
    public static void setDatapackMultiClassifications(Map<ResourceLocation, List<FoodClassification>> entries) {
        Map<ResourceLocation, List<FoodClassification>> map = new HashMap<>();
        if (entries != null) {
            entries.forEach((id, list) -> {
                if (id != null && list != null && !list.isEmpty()) {
                    List<FoodClassification> sorted = new ArrayList<>(list);
                    sorted.sort(RULE_COMPARATOR);
                    map.put(id, Collections.unmodifiableList(sorted));
                }
            });
        }
        ClassificationRuntimeState next = RUNTIME_STATE.updateAndGet(old -> new ClassificationRuntimeState(
                map, old.userOverrides(), old.activePacks(), old.skippedPacks()
        ));
        LOGGER.info("Loaded {} datapack food classification keys (atomic multi-swap)", map.size());
        FoodCompatibilityManager.applyRuntimeState(next);
    }

    /**
     * Atomically registers a single datapack entry, appending it to the candidates list for this item.
     */
    public static void registerDatapackEntry(ResourceLocation id, FoodClassification classification) {
        if (id == null || classification == null) return;
        ClassificationRuntimeState next = RUNTIME_STATE.updateAndGet(old -> {
            Map<ResourceLocation, List<FoodClassification>> updated = new HashMap<>(old.datapackEntries());
            List<FoodClassification> list = new ArrayList<>(updated.getOrDefault(id, List.of()));
            list.add(classification);
            list.sort(RULE_COMPARATOR);
            updated.put(id, Collections.unmodifiableList(list));
            return new ClassificationRuntimeState(
                    updated, old.userOverrides(), old.activePacks(), old.skippedPacks()
            );
        });
        FoodCompatibilityManager.applyRuntimeState(next);
    }

    /**
     * Returns the primary (deterministic first) datapack classification for an item.
     */
    public static Optional<FoodClassification> getDatapackClassification(ResourceLocation id) {
        if (id == null) return Optional.empty();
        List<FoodClassification> list = activeDatapackMap().get(id);
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
        List<FoodClassification> list = activeDatapackMap().get(id);
        return list != null ? list : List.of();
    }

    /**
     * Atomically registers a user override.
     */
    public static void registerUserOverride(ResourceLocation id, FoodClassification classification) {
        if (id == null || classification == null) return;
        ClassificationRuntimeState next = RUNTIME_STATE.updateAndGet(old -> {
            Map<ResourceLocation, FoodClassification> updated = new HashMap<>(old.userOverrides());
            updated.put(id, classification);
            return new ClassificationRuntimeState(
                    old.datapackEntries(), updated, old.activePacks(), old.skippedPacks()
            );
        });
        FoodCompatibilityManager.applyRuntimeState(next);
    }

    public static Optional<FoodClassification> getUserOverride(ResourceLocation id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(activeUserOverridesMap().get(id));
    }

    public static void clearDatapack() {
        ClassificationRuntimeState next = RUNTIME_STATE.updateAndGet(old -> new ClassificationRuntimeState(
                Map.of(), old.userOverrides(), old.activePacks(), old.skippedPacks()
        ));
        FoodCompatibilityManager.applyRuntimeState(next);
    }

    public static void clearUserOverrides() {
        ClassificationRuntimeState next = RUNTIME_STATE.updateAndGet(old -> new ClassificationRuntimeState(
                old.datapackEntries(), Map.of(), old.activePacks(), old.skippedPacks()
        ));
        FoodCompatibilityManager.applyRuntimeState(next);
    }

    public static void clearAll() {
        CLIENT_SYNCED_STATE.set(null);
        ClassificationRuntimeState next = ClassificationRuntimeState.EMPTY;
        RUNTIME_STATE.set(next);
        FoodCompatibilityManager.resetToDefaults(next);
    }

    /**
     * Returns an unmodifiable snapshot view of primary datapack entries (v0.1 backward-compatible).
     */
    public static Map<ResourceLocation, FoodClassification> getDatapackEntries() {
        Map<ResourceLocation, List<FoodClassification>> raw = activeDatapackMap();
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
        return activeDatapackMap();
    }

    public static Map<ResourceLocation, FoodClassification> getUserOverrides() {
        return activeUserOverridesMap();
    }

    private static Map<ResourceLocation, List<FoodClassification>> activeDatapackMap() {
        ClientSyncedClassificationState synced = getClientSyncedState();
        return synced != null ? synced.datapackEntries() : RUNTIME_STATE.get().datapackEntries();
    }

    private static Map<ResourceLocation, FoodClassification> activeUserOverridesMap() {
        ClientSyncedClassificationState synced = getClientSyncedState();
        return synced != null ? synced.userOverrides() : RUNTIME_STATE.get().userOverrides();
    }

    /**
     * Parses user overrides from CommonConfig specification into an immutable map.
     */
    public static Map<ResourceLocation, FoodClassification> parseUserOverridesFromConfig() {
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
        } catch (Exception e) {
            LOGGER.error("Failed to parse user food overrides from configuration", e);
        }
        return Collections.unmodifiableMap(tempMap);
    }

    /**
     * Parses and atomically updates user overrides from configuration without modifying datapack state.
     */
    public static void reloadUserOverridesFromConfig() {
        Map<ResourceLocation, FoodClassification> userOverrides = parseUserOverridesFromConfig();
        ClassificationRuntimeState next = RUNTIME_STATE.updateAndGet(old -> new ClassificationRuntimeState(
                old.datapackEntries(), userOverrides, old.activePacks(), old.skippedPacks()
        ));
        LOGGER.info("Loaded {} user food override classifications (atomic swap)", userOverrides.size());
        FoodCompatibilityManager.applyRuntimeState(next);
    }
}
