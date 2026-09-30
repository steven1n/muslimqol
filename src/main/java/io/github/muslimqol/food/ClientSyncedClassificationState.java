package io.github.muslimqol.food;

import io.github.muslimqol.api.ConsumptionPolicy;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of server-synchronized food classifications and consumption policies
 * applied on a remote multiplayer client.
 * <p>
 * Intentionally excludes server-only compatibility pack metadata ({@code activePacks},
 * {@code skippedPacks}, {@code packStates}) and bypasses {@code FoodCompatibilityManager.applyRuntimeState}.
 */
public record ClientSyncedClassificationState(
        Map<ResourceLocation, List<FoodClassification>> datapackEntries,
        Map<ResourceLocation, FoodClassification> userOverrides,
        ConsumptionPolicy halalPolicy,
        ConsumptionPolicy restrictedPolicy,
        ConsumptionPolicy doubtfulPolicy,
        ConsumptionPolicy unknownPolicy
) {
    public ClientSyncedClassificationState {
        Objects.requireNonNull(halalPolicy, "halalPolicy must not be null");
        Objects.requireNonNull(restrictedPolicy, "restrictedPolicy must not be null");
        Objects.requireNonNull(doubtfulPolicy, "doubtfulPolicy must not be null");
        Objects.requireNonNull(unknownPolicy, "unknownPolicy must not be null");

        if (datapackEntries == null || datapackEntries.isEmpty()) {
            datapackEntries = Map.of();
        } else {
            Map<ResourceLocation, List<FoodClassification>> copy = new LinkedHashMap<>();
            for (var entry : datapackEntries.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    copy.put(entry.getKey(), List.copyOf(entry.getValue()));
                }
            }
            datapackEntries = Collections.unmodifiableMap(copy);
        }

        if (userOverrides == null || userOverrides.isEmpty()) {
            userOverrides = Map.of();
        } else {
            userOverrides = Map.copyOf(userOverrides);
        }
    }

    /**
     * Returns the synchronized consumption policy for the given food status.
     */
    public ConsumptionPolicy getPolicy(FoodStatus status) {
        if (status == null) {
            return ConsumptionPolicy.ALLOW;
        }
        return switch (status) {
            case HALAL -> halalPolicy;
            case RESTRICTED -> restrictedPolicy;
            case DOUBTFUL -> doubtfulPolicy;
            case UNKNOWN -> unknownPolicy;
        };
    }
}
