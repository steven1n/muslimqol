package io.github.muslimqol.api;

import java.util.Objects;

/**
 * Metadata record describing the classification of a food item.
 *
 * @param status Dietary status of the item
 * @param reason Reason key or descriptive identifier for classification
 * @param source Source category of the classification
 * @param providerId Unique identifier of the provider or rule that produced this classification
 * @param priority Precedence priority tier of this classification
 */
public record FoodClassification(
    FoodStatus status,
    String reason,
    ClassificationSource source,
    ClassificationProviderId providerId,
    ClassificationPriority priority,
    ClassificationRuleId ruleId
) {
    public FoodClassification {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (reason == null) {
            reason = "unclassified";
        }
        if (providerId == null) {
            providerId = switch (source) {
                case USER_OVERRIDE -> ClassificationProviderId.USER_OVERRIDE;
                case DATAPACK -> ClassificationProviderId.DATAPACK;
                case ITEM_TAG -> ClassificationProviderId.ITEM_TAG;
                case BUILTIN -> ClassificationProviderId.BUILTIN;
            };
        }
        if (priority == null) {
            priority = ClassificationPriority.fromSource(source);
        }
    }

    /**
     * Backward-compatible 5-argument constructor.
     */
    public FoodClassification(FoodStatus status, String reason, ClassificationSource source, ClassificationProviderId providerId, ClassificationPriority priority) {
        this(status, reason, source, providerId, priority, null);
    }

    /**
     * Backward-compatible 3-argument constructor for v0.1 calls.
     */
    public FoodClassification(FoodStatus status, String reason, ClassificationSource source) {
        this(status, reason, source, null, null, null);
    }

    /**
     * Convenience constructor with explicit provider identity.
     */
    public FoodClassification(FoodStatus status, String reason, ClassificationSource source, ClassificationProviderId providerId) {
        this(status, reason, source, providerId, ClassificationPriority.fromSource(source), null);
    }

    /**
     * Resolves the localization key for the reason message.
     */
    public String getReasonTranslationKey() {
        if (reason.isBlank()) {
            return "food_reason.muslimqol.unclassified";
        }
        if (reason.startsWith("food_reason.")) {
            return reason;
        }
        return "food_reason.muslimqol." + reason;
    }

    /**
     * Default unclassified fallback instance.
     */
    public static FoodClassification unknown() {
        return new FoodClassification(
                FoodStatus.UNKNOWN,
                "unclassified",
                ClassificationSource.BUILTIN,
                ClassificationProviderId.BUILTIN,
                ClassificationPriority.UNKNOWN
        );
    }
}
