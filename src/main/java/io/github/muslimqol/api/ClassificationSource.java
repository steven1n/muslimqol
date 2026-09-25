package io.github.muslimqol.api;

/**
 * Indicates where a food classification originates from, establishing precedence.
 */
public enum ClassificationSource {
    BUILTIN,
    ITEM_TAG,
    DATAPACK,
    USER_OVERRIDE
}
