package io.github.muslimqol.compat;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable representation of a compatibility pack's runtime status.
 *
 * @param namespace Datapack namespace containing the compatibility pack
 * @param metadata Compatibility metadata loaded from {@code compatibility.json}
 * @param verificationStatus Deterministic verification status (VERIFIED, UNVERIFIED, SKIPPED)
 * @param installedVersion Resolved version of the target mod, or null if mod is absent/unversioned
 */
public record CompatibilityPackState(
    String namespace,
    CompatibilityMetadata metadata,
    CompatibilityVerificationStatus verificationStatus,
    String installedVersion
) {
    public CompatibilityPackState {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(verificationStatus, "verificationStatus must not be null");
    }

    public boolean isActive() {
        return verificationStatus != CompatibilityVerificationStatus.SKIPPED;
    }

    public boolean isVerified() {
        return verificationStatus == CompatibilityVerificationStatus.VERIFIED;
    }

    public boolean isUnverified() {
        return verificationStatus == CompatibilityVerificationStatus.UNVERIFIED;
    }

    public boolean isSkipped() {
        return verificationStatus == CompatibilityVerificationStatus.SKIPPED;
    }

    public Optional<String> getInstalledVersion() {
        return Optional.ofNullable(installedVersion);
    }
}
