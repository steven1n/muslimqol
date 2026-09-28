package io.github.muslimqol.prayer;

/**
 * Daily calculated solar and prayer events in chronological order.
 *
 * <p>Note: {@link #SUNRISE} is included as a calculated solar boundary (marking the end of the Fajr window),
 * although it is not one of the five obligatory daily prayers.
 */
public enum Prayer {
    FAJR("prayer.muslimqol.fajr", true),
    SUNRISE("prayer.muslimqol.sunrise", false),
    DHUHR("prayer.muslimqol.dhuhr", true),
    ASR("prayer.muslimqol.asr", true),
    MAGHRIB("prayer.muslimqol.maghrib", true),
    ISHA("prayer.muslimqol.isha", true);

    private final String translationKey;
    private final boolean obligatoryPrayer;

    Prayer(String translationKey, boolean obligatoryPrayer) {
        this.translationKey = translationKey;
        this.obligatoryPrayer = obligatoryPrayer;
    }

    /**
     * Returns the localization key for this event in {@code en_us.json} and {@code ar_sa.json}.
     */
    public String translationKey() {
        return translationKey;
    }

    /**
     * Returns true if this event represents one of the five daily obligatory prayers,
     * or false if it is a calculated solar boundary ({@link #SUNRISE}).
     */
    public boolean isObligatoryPrayer() {
        return obligatoryPrayer;
    }
}
