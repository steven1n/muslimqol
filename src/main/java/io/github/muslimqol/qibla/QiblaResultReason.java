package io.github.muslimqol.qibla;

/**
 * Outcome status for Qibla bearing calculation.
 */
public enum QiblaResultReason {
    /**
     * Initial great-circle bearing calculated successfully.
     */
    OK,

    /**
     * Observer is coincident with the Kaaba location; bearing is undefined.
     */
    AT_KAABA,

    /**
     * Observer is antipodal to the Kaaba; all directions are equidistant and bearing is mathematically indeterminate.
     */
    ANTIPODAL
}
