package io.github.muslimqol.api;

/**
 * Precedence tiers for food classification providers.
 * <p>
 * Evaluation order:
 * {@code USER_OVERRIDE (400)} &gt;
 * {@code DATAPACK (300)} &gt;
 * {@code ITEM_TAG (200)} &gt;
 * {@code BUILTIN (100)} &gt;
 * {@code UNKNOWN (0)}.
 */
public enum ClassificationPriority implements Comparable<ClassificationPriority> {
    UNKNOWN(0),
    BUILTIN(100),
    ITEM_TAG(200),
    DATAPACK(300),
    USER_OVERRIDE(400);

    private final int level;

    ClassificationPriority(int level) {
        this.level = level;
    }

    /**
     * Numeric weight representing this priority tier. Higher values take precedence.
     */
    public int level() {
        return level;
    }

    /**
     * Maps legacy {@link ClassificationSource} to its canonical priority tier.
     */
    public static ClassificationPriority fromSource(ClassificationSource source) {
        if (source == null) {
            return UNKNOWN;
        }
        return switch (source) {
            case USER_OVERRIDE -> USER_OVERRIDE;
            case DATAPACK -> DATAPACK;
            case ITEM_TAG -> ITEM_TAG;
            case BUILTIN -> BUILTIN;
        };
    }
}
