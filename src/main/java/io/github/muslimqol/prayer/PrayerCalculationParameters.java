package io.github.muslimqol.prayer;

import java.util.Objects;

/**
 * Immutable configuration parameters for daily prayer time calculation.
 *
 * @param method           selected calculation method preset or {@link CalculationMethod#CUSTOM}
 * @param fajrAngleDeg     solar depression angle below horizon for Fajr in degrees [1.0, 30.0]
 * @param ishaAngleDeg     solar depression angle below horizon for Isha in degrees [1.0, 30.0]
 * @param asrMethod        juristic shadow-factor method for Asr
 * @param highLatitudeRule fallback rule when astronomical twilight is unavailable
 * @param adjustments      per-event minute adjustments
 */
public record PrayerCalculationParameters(
        CalculationMethod method,
        double fajrAngleDeg,
        double ishaAngleDeg,
        AsrMethod asrMethod,
        HighLatitudeRule highLatitudeRule,
        PrayerAdjustments adjustments
) {

    public static final double MIN_TWILIGHT_ANGLE_DEG = 1.0;
    public static final double MAX_TWILIGHT_ANGLE_DEG = 30.0;

    public PrayerCalculationParameters {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(asrMethod, "asrMethod must not be null");
        Objects.requireNonNull(highLatitudeRule, "highLatitudeRule must not be null");
        Objects.requireNonNull(adjustments, "adjustments must not be null");

        validateAngle("fajrAngleDeg", fajrAngleDeg);
        validateAngle("ishaAngleDeg", ishaAngleDeg);
    }

    private static void validateAngle(String name, double angleDeg) {
        if (!Double.isFinite(angleDeg)
                || angleDeg < MIN_TWILIGHT_ANGLE_DEG
                || angleDeg > MAX_TWILIGHT_ANGLE_DEG) {
            throw new IllegalArgumentException(name + " must be finite and between "
                    + MIN_TWILIGHT_ANGLE_DEG + " and " + MAX_TWILIGHT_ANGLE_DEG + ": " + angleDeg);
        }
    }

    public static PrayerCalculationParameters of(CalculationMethod method) {
        return of(method, AsrMethod.STANDARD, HighLatitudeRule.MIDDLE_OF_NIGHT, PrayerAdjustments.NONE);
    }

    public static PrayerCalculationParameters of(
            CalculationMethod method,
            AsrMethod asrMethod,
            HighLatitudeRule highLatitudeRule
    ) {
        return of(method, asrMethod, highLatitudeRule, PrayerAdjustments.NONE);
    }

    public static PrayerCalculationParameters of(
            CalculationMethod method,
            AsrMethod asrMethod,
            HighLatitudeRule highLatitudeRule,
            PrayerAdjustments adjustments
    ) {
        Objects.requireNonNull(method, "method must not be null");
        return new PrayerCalculationParameters(
                method,
                method.defaultFajrAngleDeg(),
                method.defaultIshaAngleDeg(),
                asrMethod,
                highLatitudeRule,
                adjustments
        );
    }

    public static PrayerCalculationParameters custom(
            double fajrAngleDeg,
            double ishaAngleDeg,
            AsrMethod asrMethod,
            HighLatitudeRule highLatitudeRule,
            PrayerAdjustments adjustments
    ) {
        return new PrayerCalculationParameters(
                CalculationMethod.CUSTOM,
                fajrAngleDeg,
                ishaAngleDeg,
                asrMethod,
                highLatitudeRule,
                adjustments
        );
    }

    public PrayerCalculationParameters withAdjustments(PrayerAdjustments newAdjustments) {
        return new PrayerCalculationParameters(
                method,
                fajrAngleDeg,
                ishaAngleDeg,
                asrMethod,
                highLatitudeRule,
                newAdjustments
        );
    }

    public PrayerCalculationParameters withAsrMethod(AsrMethod newAsrMethod) {
        return new PrayerCalculationParameters(
                method,
                fajrAngleDeg,
                ishaAngleDeg,
                newAsrMethod,
                highLatitudeRule,
                adjustments
        );
    }

    public PrayerCalculationParameters withHighLatitudeRule(HighLatitudeRule newRule) {
        return new PrayerCalculationParameters(
                method,
                fajrAngleDeg,
                ishaAngleDeg,
                asrMethod,
                newRule,
                adjustments
        );
    }
}
