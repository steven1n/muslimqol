package io.github.muslimqol.util;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Utility methods for handling ResourceLocation safely across Minecraft 1.21.1.
 */
public final class ResourceLocationUtil {

    private ResourceLocationUtil() {}

    /**
     * Parses a string into a ResourceLocation, defaulting to "minecraft" namespace if omitted.
     */
    public static Optional<ResourceLocation> tryParse(String locationString) {
        if (locationString == null || locationString.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(locationString.trim()));
    }

    /**
     * Creates a ResourceLocation from namespace and path.
     */
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /**
     * Creates a ResourceLocation in the "muslimqol" mod namespace.
     */
    public static ResourceLocation modLoc(String path) {
        return ResourceLocation.fromNamespaceAndPath("muslimqol", path);
    }
}
