package io.github.muslimqol.compat;

import com.google.gson.JsonObject;

import java.util.Optional;

/**
 * Optional metadata descriptor for compatibility datapacks located at
 * {@code data/<namespace>/muslimqol/compatibility.json}.
 *
 * @param format Format version number (e.g. 1)
 * @param name Human-readable name of the compatibility pack
 * @param targetMod Required mod ID for this compatibility pack (null or empty if universally applicable)
 */
public record CompatibilityMetadata(
    int format,
    String name,
    String targetMod
) {
    public static final int CURRENT_FORMAT = 1;

    public static Optional<CompatibilityMetadata> fromJson(JsonObject obj) {
        if (obj == null) {
            return Optional.empty();
        }
        int format = obj.has("format") ? obj.get("format").getAsInt() : CURRENT_FORMAT;
        String name = obj.has("name") ? obj.get("name").getAsString() : "Unnamed Compatibility Pack";
        String targetMod = null;
        if (obj.has("target_mod") && !obj.get("target_mod").isJsonNull()) {
            targetMod = obj.get("target_mod").getAsString().trim();
            if (targetMod.isEmpty()) {
                targetMod = null;
            }
        }
        return Optional.of(new CompatibilityMetadata(format, name, targetMod));
    }
}
