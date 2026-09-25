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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Central manager and registry for food classification providers, snapshots, and compatibility packs.
 */
public final class FoodCompatibilityManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodCompatibilityManager.class);

    // Default ModList loader checker safely guarded against offline test execution
    private static Predicate<String> modLoadedChecker = FoodCompatibilityManager::defaultIsModLoaded;

    // Registered providers maintained as an ordered map of id -> provider
    private static final Map<ClassificationProviderId, FoodClassificationProvider> REGISTERED_PROVIDERS = new LinkedHashMap<>();
    private static Map<String, CompatibilityMetadata> activePacks = new LinkedHashMap<>();
    private static Map<String, CompatibilityMetadata> skippedPacks = new LinkedHashMap<>();

    // Active immutable snapshot swapped atomically
    private static final AtomicReference<CompatibilitySnapshot> SNAPSHOT_REF = new AtomicReference<>();

    static {
        registerDefaultProviders();
        rebuildSnapshot();
    }

    private FoodCompatibilityManager() {}

    private static boolean defaultIsModLoaded(String modId) {
        try {
            var modList = net.neoforged.fml.ModList.get();
            return modList != null && modList.isLoaded(modId);
        } catch (Throwable ignored) {
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

    private static synchronized void registerDefaultProviders() {
        REGISTERED_PROVIDERS.clear();

        // 1. User Override Provider
        REGISTERED_PROVIDERS.put(ClassificationProviderId.USER_OVERRIDE, new FoodClassificationProvider() {
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
                return FoodClassificationRegistry.getUserOverride(itemId);
            }
        });

        // 2. Datapack Provider
        REGISTERED_PROVIDERS.put(ClassificationProviderId.DATAPACK, new FoodClassificationProvider() {
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
                return FoodClassificationRegistry.getDatapackClassification(itemId);
            }
        });

        // 3. Item Tag Provider
        REGISTERED_PROVIDERS.put(ClassificationProviderId.ITEM_TAG, new FoodClassificationProvider() {
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
        REGISTERED_PROVIDERS.put(ClassificationProviderId.BUILTIN, new FoodClassificationProvider() {
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
                return BuiltinFoodData.getClassification(itemId);
            }
        });
    }

    /**
     * Rebuilds and atomically updates the active immutable compatibility snapshot.
     */
    public static synchronized void rebuildSnapshot() {
        List<FoodClassificationProvider> providerList = new ArrayList<>(REGISTERED_PROVIDERS.values());
        CompatibilitySnapshot newSnapshot = new CompatibilitySnapshot(providerList, activePacks, skippedPacks);
        SNAPSHOT_REF.set(newSnapshot);
    }

    /**
     * Registers a new custom food classification provider and updates the active snapshot.
     */
    public static synchronized void registerProvider(FoodClassificationProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        REGISTERED_PROVIDERS.put(provider.id(), provider);
        rebuildSnapshot();
        LOGGER.info("Registered food classification provider '{}' (priority {})",
                provider.id(), provider.priority());
    }

    /**
     * Unregisters a provider by its ID.
     */
    public static synchronized boolean unregisterProvider(ClassificationProviderId id) {
        if (id == null) return false;
        if (REGISTERED_PROVIDERS.remove(id) != null) {
            rebuildSnapshot();
            LOGGER.info("Unregistered food classification provider '{}'", id);
            return true;
        }
        return false;
    }

    /**
     * Updates recorded compatibility pack metadata and rebuilds snapshot.
     */
    public static synchronized void updateCompatibilityPacks(
            Map<String, CompatibilityMetadata> active,
            Map<String, CompatibilityMetadata> skipped
    ) {
        activePacks = active != null ? new LinkedHashMap<>(active) : new LinkedHashMap<>();
        skippedPacks = skipped != null ? new LinkedHashMap<>(skipped) : new LinkedHashMap<>();
        rebuildSnapshot();
    }

    /**
     * Resets manager state to built-in defaults (clearing third-party providers and pack metadata).
     */
    public static synchronized void resetToDefaults() {
        activePacks.clear();
        skippedPacks.clear();
        registerDefaultProviders();
        rebuildSnapshot();
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
