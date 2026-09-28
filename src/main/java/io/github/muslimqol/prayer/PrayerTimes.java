package io.github.muslimqol.prayer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable calculated daily prayer schedule for a civil date and time zone.
 *
 * <p>Privacy note: This object intentionally excludes observer latitude and longitude so that
 * schedules and diagnostic outputs cannot leak personal location coordinates.
 */
public final class PrayerTimes {

    private final LocalDate civilDate;
    private final ZoneId zoneId;
    private final CalculationMethod calculationMethod;
    private final AsrMethod asrMethod;
    private final HighLatitudeRule highLatitudeRule;
    private final Map<Prayer, PrayerMoment> moments;

    public PrayerTimes(
            LocalDate civilDate,
            ZoneId zoneId,
            CalculationMethod calculationMethod,
            AsrMethod asrMethod,
            HighLatitudeRule highLatitudeRule,
            Map<Prayer, PrayerMoment> moments
    ) {
        this.civilDate = Objects.requireNonNull(civilDate, "civilDate must not be null");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
        this.calculationMethod = Objects.requireNonNull(calculationMethod, "calculationMethod must not be null");
        this.asrMethod = Objects.requireNonNull(asrMethod, "asrMethod must not be null");
        this.highLatitudeRule = Objects.requireNonNull(highLatitudeRule, "highLatitudeRule must not be null");
        Objects.requireNonNull(moments, "moments must not be null");

        EnumMap<Prayer, PrayerMoment> copy = new EnumMap<>(Prayer.class);
        for (Prayer prayer : Prayer.values()) {
            PrayerMoment moment = moments.get(prayer);
            if (moment == null) {
                throw new IllegalArgumentException("Missing PrayerMoment for " + prayer);
            }
            if (moment.prayer() != prayer) {
                throw new IllegalArgumentException("Mismatched prayer key " + prayer + " and moment " + moment.prayer());
            }
            copy.put(prayer, moment);
        }
        this.moments = Collections.unmodifiableMap(copy);
    }

    public LocalDate civilDate() {
        return civilDate;
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    public CalculationMethod calculationMethod() {
        return calculationMethod;
    }

    public AsrMethod asrMethod() {
        return asrMethod;
    }

    public HighLatitudeRule highLatitudeRule() {
        return highLatitudeRule;
    }

    public Map<Prayer, PrayerMoment> moments() {
        return moments;
    }

    public PrayerMoment moment(Prayer prayer) {
        Objects.requireNonNull(prayer, "prayer must not be null");
        return moments.get(prayer);
    }

    public Optional<Instant> instant(Prayer prayer) {
        return moment(prayer).instant();
    }

    /**
     * Converts the resolved {@link Instant} for the requested event into a {@link ZonedDateTime}
     * in {@link #zoneId()}, or returns {@link Optional#empty()} if the event is unavailable.
     */
    public Optional<ZonedDateTime> zonedDateTime(Prayer prayer) {
        return instant(prayer).map(inst -> ZonedDateTime.ofInstant(inst, zoneId));
    }

    public PrayerMoment fajr() {
        return moments.get(Prayer.FAJR);
    }

    public PrayerMoment sunrise() {
        return moments.get(Prayer.SUNRISE);
    }

    public PrayerMoment dhuhr() {
        return moments.get(Prayer.DHUHR);
    }

    public PrayerMoment asr() {
        return moments.get(Prayer.ASR);
    }

    public PrayerMoment maghrib() {
        return moments.get(Prayer.MAGHRIB);
    }

    public PrayerMoment isha() {
        return moments.get(Prayer.ISHA);
    }

    /**
     * Returns true if all six daily events have resolved timestamps.
     */
    public boolean allDefined() {
        for (PrayerMoment moment : moments.values()) {
            if (!moment.isAvailable()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrayerTimes that)) return false;
        return Objects.equals(civilDate, that.civilDate)
                && Objects.equals(zoneId, that.zoneId)
                && calculationMethod == that.calculationMethod
                && asrMethod == that.asrMethod
                && highLatitudeRule == that.highLatitudeRule
                && Objects.equals(moments, that.moments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(civilDate, zoneId, calculationMethod, asrMethod, highLatitudeRule, moments);
    }

    @Override
    public String toString() {
        return "PrayerTimes{"
                + "civilDate=" + civilDate
                + ", zoneId=" + zoneId
                + ", calculationMethod=" + calculationMethod
                + ", asrMethod=" + asrMethod
                + ", highLatitudeRule=" + highLatitudeRule
                + ", moments=" + moments
                + '}';
    }
}
