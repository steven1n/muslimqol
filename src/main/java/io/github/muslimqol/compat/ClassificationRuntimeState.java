package io.github.muslimqol.compat;

import io.github.muslimqol.api.FoodClassification;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot representing a single coherent generation of food classifications,
 * user overrides, and compatibility metadata.
 *
 * @param datapackEntries Multi-candidate datapack entries mapped by item identifier
 * @param userOverrides User override classifications mapped by item identifier
 * @param activePacks Metadata for active compatibility packs mapped by namespace
 * @param skippedPacks Metadata for skipped compatibility packs mapped by namespace
 */
public record ClassificationRuntimeState(
    Map<ResourceLocation, List<FoodClassification>> datapackEntries,
    Map<ResourceLocation, FoodClassification> userOverrides,
    Map<String, CompatibilityMetadata> activePacks,
    Map<String, CompatibilityMetadata> skippedPacks
) {
    public static final ClassificationRuntimeState EMPTY = new ClassificationRuntimeState(
            Map.of(), Map.of(), Map.of(), Map.of()
    );

    public ClassificationRuntimeState {
        datapackEntries = datapackEntries == null ? Map.of() : Map.copyOf(datapackEntries);
        userOverrides = userOverrides == null ? Map.of() : Map.copyOf(userOverrides);
        activePacks = activePacks == null ? Map.of() : Map.copyOf(activePacks);
        skippedPacks = skippedPacks == null ? Map.of() : Map.copyOf(skippedPacks);
    }
}
