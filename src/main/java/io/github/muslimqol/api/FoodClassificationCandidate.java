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
    ClassificationPriority priority,
    ClassificationRuleId ruleId
) implements Comparable<FoodClassificationCandidate> {

    public FoodClassificationCandidate {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(classification, "classification must not be null");
        if (priority == null) {
            priority = classification.priority();
        }
        if (ruleId == null) {
            ruleId = classification.ruleId();
        }
    }

    /**
     * Backward-compatible 3-argument constructor.
     */
    public FoodClassificationCandidate(
            ClassificationProviderId providerId,
            FoodClassification classification,
            ClassificationPriority priority
    ) {
        this(providerId, classification, priority, classification != null ? classification.ruleId() : null);
    }

    @Override
    public int compareTo(FoodClassificationCandidate other) {
        if (other == null) {
            return -1;
        }
        // 1. Higher priority tier wins (descending numeric level)
        int p = Integer.compare(other.priority().level(), this.priority().level());
        if (p != 0) {
            return p;
        }
        // 2. Provider ID lexicographical order ASC
        int provCmp = this.providerId.compareTo(other.providerId);
        if (provCmp != 0) {
            return provCmp;
        }
        // 3. Rule / source ID lexicographical order ASC
        ClassificationRuleId thisRule = this.ruleId != null ? this.ruleId : this.classification.ruleId();
        ClassificationRuleId otherRule = other.ruleId != null ? other.ruleId : other.classification.ruleId();
        if (thisRule != null && otherRule != null) {
            int ruleCmp = thisRule.compareTo(otherRule);
            if (ruleCmp != 0) {
                return ruleCmp;
            }
        } else if (thisRule != null) {
            return -1;
        } else if (otherRule != null) {
            return 1;
        }
        // 4. Stable final tie-breaker for identical rule IDs: status enum name, then reason
        int statusCmp = this.classification.status().name().compareTo(other.classification.status().name());
        if (statusCmp != 0) {
            return statusCmp;
        }
        return this.classification.reason().compareTo(other.classification.reason());
    }
}
