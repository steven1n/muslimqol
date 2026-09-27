package io.github.muslimqol.compat;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Optional metadata descriptor for compatibility datapacks located at
 * {@code data/<namespace>/muslimqol/compatibility.json}.
 *
 * @param format Format version number (currently 1)
 * @param name Human-readable name of the compatibility pack
 * @param targetMod Required mod ID for this compatibility pack (null or empty if universally applicable)
 * @param targetVersion Target mod version this pack was verified against (null if unversioned legacy pack)
 * @param referenceJarSha256 SHA-256 hash of the reference mod JAR used for auditing (informational only)
 */
public record CompatibilityMetadata(
    int format,
    String name,
    String targetMod,
    String targetVersion,
    String referenceJarSha256
) {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompatibilityMetadata.class);
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");

    public static final int CURRENT_FORMAT = 1;

    public CompatibilityMetadata(int format, String name, String targetMod) {
        this(format, name, targetMod, null, null);
    }

    public static MetadataParseResult parse(JsonObject obj) {
        if (obj == null) {
            return MetadataParseResult.invalid("Metadata JSON object is null");
        }

        if (!obj.has("format")) {
            LOGGER.warn("Compatibility metadata missing 'format' field. Defaulting to format 1.");
        }

        int format;
        try {
            format = obj.has("format") ? obj.get("format").getAsInt() : CURRENT_FORMAT;
        } catch (Exception e) {
            LOGGER.warn("Invalid format value in compatibility metadata: {}", e.getMessage());
            return MetadataParseResult.invalid("Invalid format value: " + e.getMessage());
        }

        if (format != CURRENT_FORMAT) {
            LOGGER.warn("Unsupported compatibility metadata format version: {} (expected {}).",
                    format, CURRENT_FORMAT);
            return MetadataParseResult.invalid("Unsupported format version: " + format);
        }

        String name = "Unnamed Compatibility Pack";
        if (obj.has("name")) {
            var nameElem = obj.get("name");
            if (!nameElem.isJsonNull()) {
                if (!nameElem.isJsonPrimitive() || !nameElem.getAsJsonPrimitive().isString()) {
                    return MetadataParseResult.invalid("Invalid 'name' field: expected string");
                }
                String trimmed = nameElem.getAsString().trim();
                if (!trimmed.isEmpty()) {
                    name = trimmed;
                }
            }
        }

        String targetMod = null;
        if (obj.has("target_mod")) {
            var modElem = obj.get("target_mod");
            if (!modElem.isJsonNull()) {
                if (!modElem.isJsonPrimitive() || !modElem.getAsJsonPrimitive().isString()) {
                    return MetadataParseResult.invalid("Invalid 'target_mod' field: expected string");
                }
                String trimmed = modElem.getAsString().trim();
                if (!trimmed.isEmpty()) {
                    targetMod = trimmed;
                }
            }
        }

        String targetVersion = null;
        if (obj.has("target_version")) {
            var verElem = obj.get("target_version");
            if (!verElem.isJsonNull()) {
                if (!verElem.isJsonPrimitive() || !verElem.getAsJsonPrimitive().isString()) {
                    return MetadataParseResult.invalid("Invalid 'target_version' field: expected string");
                }
                String trimmed = verElem.getAsString().trim();
                if (!trimmed.isEmpty()) {
                    targetVersion = trimmed;
                }
            }
        }

        String referenceJarSha256 = null;
        if (obj.has("reference_jar_sha256")) {
            var shaElem = obj.get("reference_jar_sha256");
            if (!shaElem.isJsonNull()) {
                if (!shaElem.isJsonPrimitive() || !shaElem.getAsJsonPrimitive().isString()) {
                    return MetadataParseResult.invalid("Invalid 'reference_jar_sha256' field: expected string");
                }
                String trimmed = shaElem.getAsString().trim();
                if (!trimmed.isEmpty()) {
                    if (!SHA256_PATTERN.matcher(trimmed).matches()) {
                        return MetadataParseResult.invalid("Invalid reference_jar_sha256 format: expected 64 hexadecimal characters");
                    }
                    referenceJarSha256 = trimmed.toLowerCase(Locale.ROOT);
                }
            }
        }

        return MetadataParseResult.valid(new CompatibilityMetadata(
                CURRENT_FORMAT, name, targetMod, targetVersion, referenceJarSha256
        ));
    }

    public static Optional<CompatibilityMetadata> fromJson(JsonObject obj) {
        MetadataParseResult result = parse(obj);
        if (result instanceof MetadataParseResult.Valid valid) {
            return Optional.of(valid.metadata());
        }
        return Optional.empty();
    }
}
