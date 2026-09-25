package io.github.muslimqol.compat;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Optional metadata descriptor for compatibility datapacks located at
 * {@code data/<namespace>/muslimqol/compatibility.json}.
 *
 * @param format Format version number (currently 1)
 * @param name Human-readable name of the compatibility pack
 * @param targetMod Required mod ID for this compatibility pack (null or empty if universally applicable)
 */
public record CompatibilityMetadata(
    int format,
    String name,
    String targetMod
) {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompatibilityMetadata.class);

    public static final int CURRENT_FORMAT = 1;

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

        String name = obj.has("name") && obj.get("name").isJsonPrimitive() ? obj.get("name").getAsString() : "Unnamed Compatibility Pack";
        String targetMod = null;
        if (obj.has("target_mod") && !obj.get("target_mod").isJsonNull()) {
            targetMod = obj.get("target_mod").getAsString().trim();
            if (targetMod.isEmpty()) {
                targetMod = null;
            }
        }

        return MetadataParseResult.valid(new CompatibilityMetadata(CURRENT_FORMAT, name, targetMod));
    }

    public static Optional<CompatibilityMetadata> fromJson(JsonObject obj) {
        MetadataParseResult result = parse(obj);
        if (result instanceof MetadataParseResult.Valid valid) {
            return Optional.of(valid.metadata());
        }
        return Optional.empty();
    }
}
