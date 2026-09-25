package io.github.muslimqol.api;

import java.util.List;
import java.util.Objects;

/**
 * Diagnostic and resolution result describing how a food item's classification was resolved.
 *
 * @param selected The winning classification
 * @param candidates All collected candidate classifications across providers
 * @param conflicted True if multiple providers at the highest matching priority returned conflicting statuses
 */
public record ClassificationResolution(
    FoodClassification selected,
    List<FoodClassificationCandidate> candidates,
    boolean conflicted
) {
    public ClassificationResolution {
        Objects.requireNonNull(selected, "selected must not be null");
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /**
     * Helper to wrap a single classification without multi-provider candidates.
     */
    public static ClassificationResolution ofSingle(FoodClassification selected) {
        FoodClassificationCandidate candidate = new FoodClassificationCandidate(
                selected.providerId(), selected, selected.priority()
        );
        return new ClassificationResolution(selected, List.of(candidate), false);
    }
}
