package io.github.muslimqol.api;

import java.util.Objects;

/**
 * Metadata record describing the classification of a food item.
 *
 * @param status Dietary status of the item
 * @param reason Reason key or descriptive identifier for classification
 * @param source Source provider of the classification
 */
public record FoodClassification(
    FoodStatus status,
    String reason,
    ClassificationSource source
) {
    public FoodClassification {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (reason == null) {
            reason = "unclassified";
        }
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

    public static FoodClassification unknown() {
        return new FoodClassification(FoodStatus.UNKNOWN, "unclassified", ClassificationSource.BUILTIN);
    }
}
