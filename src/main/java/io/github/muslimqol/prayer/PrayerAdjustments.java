package io.github.muslimqol.prayer;

import java.util.Objects;

/**
 * Immutable user-configured minute offsets applied to calculated daily prayer moments.
 *
 * <p>Valid range for each event is [{@link #MIN_ADJUSTMENT_MINUTES}, {@link #MAX_ADJUSTMENT_MINUTES}] (-60 to +60 minutes).
 */
public record PrayerAdjustments(
        int fajrMinutes,
        int sunriseMinutes,
        int dhuhrMinutes,
        int asrMinutes,
        int maghribMinutes,
        int ishaMinutes
) {

    public static final int MIN_ADJUSTMENT_MINUTES = -60;
    public static final int MAX_ADJUSTMENT_MINUTES = 60;

    public static final PrayerAdjustments NONE = new PrayerAdjustments(0, 0, 0, 0, 0, 0);

    public PrayerAdjustments {
        validate("fajrMinutes", fajrMinutes);
        validate("sunriseMinutes", sunriseMinutes);
        validate("dhuhrMinutes", dhuhrMinutes);
        validate("asrMinutes", asrMinutes);
        validate("maghribMinutes", maghribMinutes);
        validate("ishaMinutes", ishaMinutes);
    }

    private static void validate(String field, int minutes) {
        if (minutes < MIN_ADJUSTMENT_MINUTES || minutes > MAX_ADJUSTMENT_MINUTES) {
            throw new IllegalArgumentException(field + " must be between "
                    + MIN_ADJUSTMENT_MINUTES + " and " + MAX_ADJUSTMENT_MINUTES + ": " + minutes);
        }
    }

    public int offsetFor(Prayer prayer) {
        Objects.requireNonNull(prayer, "prayer must not be null");
        return switch (prayer) {
            case FAJR -> fajrMinutes;
            case SUNRISE -> sunriseMinutes;
            case DHUHR -> dhuhrMinutes;
            case ASR -> asrMinutes;
            case MAGHRIB -> maghribMinutes;
            case ISHA -> ishaMinutes;
        };
    }
}
