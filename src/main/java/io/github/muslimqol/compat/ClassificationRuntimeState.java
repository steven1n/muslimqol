package io.github.muslimqol.compat;

import io.github.muslimqol.api.FoodClassification;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
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
 * @param packStates Detailed runtime verification state for each compatibility pack mapped by namespace
 */
public record ClassificationRuntimeState(
    Map<ResourceLocation, List<FoodClassification>> datapackEntries,
    Map<ResourceLocation, FoodClassification> userOverrides,
    Map<String, CompatibilityMetadata> activePacks,
    Map<String, CompatibilityMetadata> skippedPacks,
    Map<String, CompatibilityPackState> packStates
) {
    public static final ClassificationRuntimeState EMPTY = new ClassificationRuntimeState(
            Map.of(), Map.of(), Map.of(), Map.of(), Map.of()
    );

    public ClassificationRuntimeState(
            Map<ResourceLocation, List<FoodClassification>> datapackEntries,
            Map<ResourceLocation, FoodClassification> userOverrides,
            Map<String, CompatibilityMetadata> activePacks,
            Map<String, CompatibilityMetadata> skippedPacks
    ) {
        this(datapackEntries, userOverrides, activePacks, skippedPacks, computeDefaultPackStates(activePacks, skippedPacks));
    }

    public ClassificationRuntimeState {
        datapackEntries = datapackEntries == null ? Map.of() : Map.copyOf(datapackEntries);
        userOverrides = userOverrides == null ? Map.of() : Map.copyOf(userOverrides);
        activePacks = activePacks == null ? Map.of() : Map.copyOf(activePacks);
        skippedPacks = skippedPacks == null ? Map.of() : Map.copyOf(skippedPacks);
        packStates = packStates == null ? Map.of() : Map.copyOf(packStates);
    }

    public Map<String, CompatibilityPackState> verifiedPacks() {
        Map<String, CompatibilityPackState> map = new LinkedHashMap<>();
        for (var entry : packStates.entrySet()) {
            if (entry.getValue().isVerified()) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public Map<String, CompatibilityPackState> unverifiedPacks() {
        Map<String, CompatibilityPackState> map = new LinkedHashMap<>();
        for (var entry : packStates.entrySet()) {
            if (entry.getValue().isUnverified()) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, CompatibilityPackState> computeDefaultPackStates(
            Map<String, CompatibilityMetadata> active,
            Map<String, CompatibilityMetadata> skipped
    ) {
        Map<String, CompatibilityPackState> map = new LinkedHashMap<>();
        if (active != null) {
            for (var entry : active.entrySet()) {
                CompatibilityMetadata meta = entry.getValue();
                CompatibilityVerificationStatus status = (meta.targetVersion() == null || meta.targetVersion().isBlank())
                        ? CompatibilityVerificationStatus.VERIFIED
                        : CompatibilityVerificationStatus.VERIFIED;
                map.put(entry.getKey(), new CompatibilityPackState(
                        entry.getKey(), meta, status, meta.targetVersion()
                ));
            }
        }
        if (skipped != null) {
            for (var entry : skipped.entrySet()) {
                map.put(entry.getKey(), new CompatibilityPackState(
                        entry.getKey(), entry.getValue(), CompatibilityVerificationStatus.SKIPPED, null
                ));
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
