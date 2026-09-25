package io.github.muslimqol.api;

/**
 * The dietary classification status for a food item.
 */
public enum FoodStatus {
    HALAL("food_status.muslimqol.halal"),
    RESTRICTED("food_status.muslimqol.restricted"),
    DOUBTFUL("food_status.muslimqol.doubtful"),
    UNKNOWN("food_status.muslimqol.unknown");

    private final String translationKey;

    FoodStatus(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }
}
