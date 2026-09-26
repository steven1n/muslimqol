package io.github.muslimqol.compat;

import java.util.Optional;

/**
 * Functional abstraction for querying loaded mod versions at runtime.
 */
@FunctionalInterface
public interface ModVersionResolver {
    Optional<String> resolve(String modId);
}
