package io.github.muslimqol.api;

import java.util.Objects;

/**
 * Diagnostic record representing a candidate classification offered by a provider during resolution.
 *
 * @param providerId Unique identifier of the provider
 * @param classification Proposed food classification
 * @param priority Precedence priority tier
 */
public record FoodClassificationCandidate(
    ClassificationProviderId providerId,
    FoodClassification classification,
    ClassificationPriority priority
) implements Comparable<FoodClassificationCandidate> {

    public FoodClassificationCandidate {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(classification, "classification must not be null");
        if (priority == null) {
            priority = classification.priority();
        }
    }

    @Override
    public int compareTo(FoodClassificationCandidate other) {
        if (other == null) {
            return -1;
        }
        // Higher priority tier wins (descending numeric level)
        int p = Integer.compare(other.priority().level(), this.priority().level());
        if (p != 0) {
            return p;
        }
        // Deterministic tie-breaker: providerId lexicographical order
        return this.providerId.compareTo(other.providerId);
    }
}
