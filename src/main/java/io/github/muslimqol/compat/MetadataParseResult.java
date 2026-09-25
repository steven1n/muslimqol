package io.github.muslimqol.compat;

import java.util.Objects;

/**
 * Result of parsing a {@code compatibility.json} descriptor.
 * Distinguishes between absent metadata (legacy datapack behavior),
 * valid metadata (evaluates target mod), and invalid/unsupported metadata (skips pack).
 */
public sealed interface MetadataParseResult {

    record Absent() implements MetadataParseResult {}

    record Valid(CompatibilityMetadata metadata) implements MetadataParseResult {
        public Valid {
            Objects.requireNonNull(metadata, "metadata must not be null");
        }
    }

    record Invalid(String reason) implements MetadataParseResult {
        public Invalid {
            if (reason == null) {
                reason = "Unknown error";
            }
        }
    }

    static MetadataParseResult absent() {
        return new Absent();
    }

    static MetadataParseResult valid(CompatibilityMetadata metadata) {
        return new Valid(metadata);
    }

    static MetadataParseResult invalid(String reason) {
        return new Invalid(reason);
    }
}
