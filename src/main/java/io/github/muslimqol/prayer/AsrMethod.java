package io.github.muslimqol.prayer;

import java.util.Locale;
import java.util.Optional;

/**
 * Juristic shadow-length convention for calculating the start of Asr prayer.
 */
public enum AsrMethod {
    /**
     * Standard shadow factor (shadow length = 1x object height + meridian noon shadow).
     */
    STANDARD(1),

    /**
     * Hanafi shadow factor (shadow length = 2x object height + meridian noon shadow).
     */
    HANAFI(2);

    private final int shadowFactor;

    AsrMethod(int shadowFactor) {
        this.shadowFactor = shadowFactor;
    }

    public int shadowFactor() {
        return shadowFactor;
    }

    /**
     * Parses a configuration string into an {@link AsrMethod} without throwing on malformed input.
     */
    public static Optional<AsrMethod> fromConfigName(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (AsrMethod method : values()) {
            if (method.name().equals(normalized)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }
}
