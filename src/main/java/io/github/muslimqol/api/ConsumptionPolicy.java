package io.github.muslimqol.api;

/**
 * Enforcement policy when a player attempts to consume a food item.
 */
public enum ConsumptionPolicy {
    ALLOW("food_policy.muslimqol.allow"),
    WARN("food_policy.muslimqol.warn"),
    BLOCK("food_policy.muslimqol.block");

    private final String translationKey;

    ConsumptionPolicy(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }
}
