package io.github.muslimqol.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Namespace-safe, deterministic identity for food classification providers and rule sources.
 *
 * @param id ResourceLocation identifier (e.g. {@code muslimqol:builtin}, {@code farmersdelight:food_compat})
 */
public record ClassificationProviderId(ResourceLocation id) implements Comparable<ClassificationProviderId> {

    public ClassificationProviderId {
        Objects.requireNonNull(id, "id must not be null");
    }

    public static ClassificationProviderId of(ResourceLocation id) {
        return new ClassificationProviderId(id);
    }

    public static ClassificationProviderId of(String namespace, String path) {
        return new ClassificationProviderId(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public static ClassificationProviderId parse(String string) {
        return new ClassificationProviderId(ResourceLocation.parse(string));
    }

    // Well-known system provider identities
    public static final ClassificationProviderId USER_OVERRIDE = of("muslimqol", "user_override");
    public static final ClassificationProviderId DATAPACK = of("muslimqol", "datapack");
    public static final ClassificationProviderId ITEM_TAG = of("muslimqol", "item_tag");
    public static final ClassificationProviderId BUILTIN = of("muslimqol", "builtin");

    @Override
    public int compareTo(ClassificationProviderId other) {
        if (other == null) {
            return 1;
        }
        return this.id.toString().compareTo(other.id.toString());
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
