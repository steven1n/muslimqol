package io.github.muslimqol.config;

import io.github.muslimqol.api.ConsumptionPolicy;
import io.github.muslimqol.api.PigPolicy;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.List;

/**
 * Server-authoritative gameplay configuration for food consumption and entity rules.
 */
public final class CommonConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<ConsumptionPolicy> HALAL_POLICY;
    public static final ModConfigSpec.EnumValue<ConsumptionPolicy> RESTRICTED_POLICY;
    public static final ModConfigSpec.EnumValue<ConsumptionPolicy> DOUBTFUL_POLICY;
    public static final ModConfigSpec.EnumValue<ConsumptionPolicy> UNKNOWN_POLICY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> USER_OVERRIDES;

    public static final ModConfigSpec.EnumValue<PigPolicy> PIG_POLICY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Food consumption and classification settings").push("food");

        HALAL_POLICY = builder
                .comment("Enforcement policy for Halal classified food (ALLOW, WARN, BLOCK)")
                .defineEnum("halal_policy", ConsumptionPolicy.ALLOW);

        RESTRICTED_POLICY = builder
                .comment("Enforcement policy for Restricted classified food (ALLOW, WARN, BLOCK)")
                .defineEnum("restricted_policy", ConsumptionPolicy.BLOCK);

        DOUBTFUL_POLICY = builder
                .comment("Enforcement policy for Doubtful classified food (ALLOW, WARN, BLOCK)")
                .defineEnum("doubtful_policy", ConsumptionPolicy.WARN);

        UNKNOWN_POLICY = builder
                .comment("Enforcement policy for Unknown / Unclassified food (ALLOW, WARN, BLOCK)")
                .defineEnum("unknown_policy", ConsumptionPolicy.ALLOW);

        USER_OVERRIDES = builder
                .comment("User overrides for item classifications. Format: 'item_id=STATUS:reason', e.g. 'minecraft:golden_apple=HALAL:special'")
                .defineListAllowEmpty(Collections.singletonList("user_overrides"), Collections::emptyList, obj -> obj instanceof String);

        builder.pop();

        builder.comment("Pig and pork related gameplay policies").push("pigs");

        PIG_POLICY = builder
                .comment("Policy for pig spawning and drops: NORMAL, NO_NATURAL_SPAWN, NO_PORK_DROPS, DISABLED_GAMEPLAY")
                .defineEnum("policy", PigPolicy.NORMAL);

        builder.pop();

        SPEC = builder.build();
    }

    private CommonConfig() {}
}
