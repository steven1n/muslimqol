package io.github.muslimqol.compat;

/**
 * Deterministic runtime trust state for a compatibility pack:
 * <ul>
 *   <li>{@link #VERIFIED}: Target mod is loaded and its version matches the declared verified target,
 *       or the pack is a legacy unversioned pack.</li>
 *   <li>{@link #UNVERIFIED}: Target mod is loaded and the pack declares a target version,
 *       but the installed version differs or is unavailable. The pack remains active by default.</li>
 *   <li>{@link #SKIPPED}: Target mod is not loaded, or the pack metadata is invalid. The pack is inactive.</li>
 * </ul>
 */
public enum CompatibilityVerificationStatus {
    VERIFIED,
    UNVERIFIED,
    SKIPPED
}
