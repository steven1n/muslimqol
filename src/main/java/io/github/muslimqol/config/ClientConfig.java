package io.github.muslimqol.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only visual and UI configuration.
 */
public final class ClientConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SHOW_TOOLTIPS;
    public static final ModConfigSpec.BooleanValue SHOW_INVENTORY_ICONS;

    public static final ModConfigSpec.BooleanValue SHOW_HALAL_ICON;
    public static final ModConfigSpec.BooleanValue SHOW_RESTRICTED_ICON;
    public static final ModConfigSpec.BooleanValue SHOW_DOUBTFUL_ICON;
    public static final ModConfigSpec.BooleanValue SHOW_UNKNOWN_ICON;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Food UI and display settings").push("food");

        SHOW_TOOLTIPS = builder
                .comment("Show food classification information in item tooltips")
                .define("show_tooltips", true);

        SHOW_INVENTORY_ICONS = builder
                .comment("Show status icons on item slots in inventories and hotbars")
                .define("show_inventory_icons", true);

        builder.pop();

        builder.comment("Status icon visibility filters").push("display");

        SHOW_HALAL_ICON = builder
                .comment("Render icon for Halal food")
                .define("show_halal_icon", true);

        SHOW_RESTRICTED_ICON = builder
                .comment("Render icon for Restricted food")
                .define("show_restricted_icon", true);

        SHOW_DOUBTFUL_ICON = builder
                .comment("Render icon for Doubtful food")
                .define("show_doubtful_icon", true);

        SHOW_UNKNOWN_ICON = builder
                .comment("Render icon for Unknown food (default false to avoid visual clutter)")
                .define("show_unknown_icon", false);

        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {}
}
