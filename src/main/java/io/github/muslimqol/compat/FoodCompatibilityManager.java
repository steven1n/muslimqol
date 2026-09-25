package io.github.muslimqol.compat;

import io.github.muslimqol.api.ClassificationPriority;
import io.github.muslimqol.api.ClassificationProviderId;
import io.github.muslimqol.api.ClassificationResolution;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodClassificationProvider;
import io.github.muslimqol.food.BuiltinFoodData;
import io.github.muslimqol.food.FoodClassificationRegistry;
import io.github.muslimqol.food.FoodTagResolver;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Central manager and registry for food classification providers, snapshots, and compatibility packs.
 */
public final class FoodCompatibilityManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodCompatibilityManager.class);

    /**
     * Reserved system provider IDs that cannot be unregistered or overwritten by third-party code.
     */
    public static final Set<ClassificationProviderId> RESERVED_IDS = Set.of(
            ClassificationProviderId.USER_OVERRIDE,
            ClassificationProviderId.DATAPACK,
            ClassificationProviderId.ITEM_TAG,
            ClassificationProviderId.BUILTIN
    );

    // Default ModList loader checker safely guarded against offline test execution
    private static Predicate<String> modLoadedChecker = FoodCompatibilityManager::defaultIsModLoaded;

    // Registered custom providers maintained as an ordered map of id -> provider
    private static final Map<ClassificationProviderId, FoodClassificationProvider> CUSTOM_PROVIDERS = new LinkedHashMap<>();

    // Active immutable snapshot swapped atomically
    private static final AtomicReference<CompatibilitySnapshot> SNAPSHOT_REF = new AtomicReference<>();

    static {
        resetToDefaults();
    }

    private FoodCompatibilityManager() {}

    private static boolean defaultIsModLoaded(String modId) {
        try {
            var modList = net.neoforged.fml.ModList.get();
            return modList != null && modList.isLoaded(modId);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void setModLoadedChecker(Predicate<String> checker) {
        modLoadedChecker = checker != null ? checker : FoodCompatibilityManager::defaultIsModLoaded;
    }

    public static boolean isModLoaded(String modId) {
        if (modId == null || modId.isBlank()) {
            return true;
        }
        return modLoadedChecker.test(modId);
    }

    public static boolean isReserved(ClassificationProviderId id) {
        return id != null && RESERVED_IDS.contains(id);
    }

    /**
     * Builds default providers bound to a specific runtime state generation.
     */
    private static List<FoodClassificationProvider> createDefaultProviders(ClassificationRuntimeState state) {
        List<FoodClassificationProvider> list = new ArrayList<>();

        // 1. User Override Provider
        list.add(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() {
                return ClassificationProviderId.USER_OVERRIDE;
            }

            @Override
            public ClassificationPriority priority() {
                return ClassificationPriority.USER_OVERRIDE;
            }

            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                if (itemId == null) return Optional.empty();
                return Optional.ofNullable(state.userOverrides().get(itemId));
            }
        });

        // 2. Datapack Provider (preserves all multi-pack candidate classifications)
        list.add(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() {
                return ClassificationProviderId.DATAPACK;
            }

            @Override
            public ClassificationPriority priority() {
                return ClassificationPriority.DATAPACK;
            }

            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                if (itemId == null) return Optional.empty();
                List<FoodClassification> entries = state.datapackEntries().get(itemId);
                return (entries != null && !entries.isEmpty()) ? Optional.of(entries.get(0)) : Optional.empty();
            }

            @Override
            public List<FoodClassification> classifyAll(ResourceLocation itemId, ItemStack stack) {
                if (itemId == null) return List.of();
                List<FoodClassification> entries = state.datapackEntries().get(itemId);
                return entries != null ? entries : List.of();
            }
        });

        // 3. Item Tag Provider
        list.add(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() {
                return ClassificationProviderId.ITEM_TAG;
            }

            @Override
            public ClassificationPriority priority() {
                return ClassificationPriority.ITEM_TAG;
            }

            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                return stack != null
                        ? FoodTagResolver.resolveTag(stack)
                        : FoodTagResolver.resolveTag(itemId);
            }
        });

        // 4. Builtin Provider
        list.add(new FoodClassificationProvider() {
            @Override
            public ClassificationProviderId id() {
                return ClassificationProviderId.BUILTIN;
            }

            @Override
            public ClassificationPriority priority() {
                return ClassificationPriority.BUILTIN;
            }

            @Override
            public Optional<FoodClassification> classify(ResourceLocation itemId, ItemStack stack) {
                return itemId != null ? BuiltinFoodData.getClassification(itemId) : Optional.empty();
            }
        });

        return list;
    }

    /**
     * Rebuilds and atomically updates the active immutable compatibility snapshot using current runtime state.
     */
    public static synchronized void rebuildSnapshot() {
        rebuildSnapshot(FoodClassificationRegistry.getRuntimeState());
    }

    /**
     * Rebuilds and atomically updates the snapshot for a specific runtime state generation.
     */
    public static synchronized void rebuildSnapshot(ClassificationRuntimeState state) {
        List<FoodClassificationProvider> providerList = new ArrayList<>(CUSTOM_PROVIDERS.values());
        providerList.addAll(createDefaultProviders(state != null ? state : ClassificationRuntimeState.EMPTY));
        CompatibilitySnapshot newSnapshot = new CompatibilitySnapshot(providerList, state);
        SNAPSHOT_REF.set(newSnapshot);
    }

    /**
     * Applies a complete new runtime state generation transactionally.
     */
    public static void applyRuntimeState(ClassificationRuntimeState state) {
        rebuildSnapshot(state);
    }

    /**
     * Registers a new custom food classification provider and updates the active snapshot.
     *
     * @throws IllegalArgumentException if the provider attempts to use a reserved system provider ID.
     */
    public static synchronized void registerProvider(FoodClassificationProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        if (isReserved(provider.id())) {
            throw new IllegalArgumentException("Cannot register custom provider with reserved system ID: " + provider.id());
        }
        CUSTOM_PROVIDERS.put(provider.id(), provider);
        rebuildSnapshot();
        LOGGER.info("Registered food classification provider '{}' (priority {})",
                provider.id(), provider.priority());
    }

    /**
     * Unregisters a custom provider by its ID.
     * Reserved system providers cannot be unregistered.
     *
     * @return true if a provider was removed, false otherwise
     */
    public static synchronized boolean unregisterProvider(ClassificationProviderId id) {
        if (id == null) return false;
        if (isReserved(id)) {
            LOGGER.warn("Cannot unregister reserved system provider '{}'", id);
            return false;
        }
        if (CUSTOM_PROVIDERS.remove(id) != null) {
            rebuildSnapshot();
            LOGGER.info("Unregistered food classification provider '{}'", id);
            return true;
        }
        return false;
    }

    /**
     * Updates recorded compatibility pack metadata and rebuilds snapshot transactionally.
     */
    public static synchronized void updateCompatibilityPacks(
            Map<String, CompatibilityMetadata> active,
            Map<String, CompatibilityMetadata> skipped
    ) {
        ClassificationRuntimeState current = FoodClassificationRegistry.getRuntimeState();
        ClassificationRuntimeState next = new ClassificationRuntimeState(
                current.datapackEntries(),
                current.userOverrides(),
                active,
                skipped
        );
        FoodClassificationRegistry.applyRuntimeState(next);
    }

    /**
     * Resets manager state to built-in defaults (clearing third-party providers and pack metadata).
     */
    public static synchronized void resetToDefaults() {
        resetToDefaults(ClassificationRuntimeState.EMPTY);
    }

    public static synchronized void resetToDefaults(ClassificationRuntimeState state) {
        CUSTOM_PROVIDERS.clear();
        rebuildSnapshot(state);
    }

    public static CompatibilitySnapshot getActiveSnapshot() {
        CompatibilitySnapshot snapshot = SNAPSHOT_REF.get();
        if (snapshot == null) {
            rebuildSnapshot();
            return SNAPSHOT_REF.get();
        }
        return snapshot;
    }

    /**
     * Resolves food classification with full candidate diagnostics and conflict checking.
     */
    public static ClassificationResolution resolve(ResourceLocation itemId, ItemStack stack) {
        return getActiveSnapshot().resolve(itemId, stack);
    }

    /**
     * Resolves food classification fast-path.
     */
    public static FoodClassification classify(ResourceLocation itemId, ItemStack stack) {
        return getActiveSnapshot().classify(itemId, stack);
    }
}
