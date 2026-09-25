package io.github.muslimqol.api;

/**
 * Gameplay control policy regarding pigs and pork items.
 * Does not remove registries or entity types.
 */
public enum PigPolicy {
    NORMAL,
    NO_NATURAL_SPAWN,
    NO_PORK_DROPS,
    DISABLED_GAMEPLAY
}
