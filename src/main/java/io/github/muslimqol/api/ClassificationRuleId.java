package io.github.muslimqol.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Unique identifier for a specific classification rule or source definition.
 * For example: {@code examplepack:food_classifications/beef_rules}.
 *
 * @param id Namespace-safe ResourceLocation identifying the rule
 */
public record ClassificationRuleId(ResourceLocation id) implements Comparable<ClassificationRuleId> {

    public ClassificationRuleId {
        Objects.requireNonNull(id, "rule id must not be null");
    }

    public static ClassificationRuleId of(String namespace, String path) {
        return new ClassificationRuleId(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public static ClassificationRuleId parse(String string) {
        return new ClassificationRuleId(ResourceLocation.parse(string));
    }

    @Override
    public int compareTo(ClassificationRuleId other) {
        if (other == null) {
            return -1;
        }
        return this.id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
