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

    public static final ModConfigSpec.BooleanValue QIBLA_ENABLED;
    public static final ModConfigSpec.BooleanValue QIBLA_LOCATION_CONFIGURED;
    public static final ModConfigSpec.DoubleValue QIBLA_LATITUDE;
    public static final ModConfigSpec.DoubleValue QIBLA_LONGITUDE;
    public static final ModConfigSpec.BooleanValue QIBLA_HUD_ENABLED;

    public static final ModConfigSpec.BooleanValue PRAYER_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> PRAYER_ZONE_ID;
    public static final ModConfigSpec.ConfigValue<String> PRAYER_CALCULATION_METHOD;
    public static final ModConfigSpec.DoubleValue PRAYER_CUSTOM_FAJR_ANGLE;
    public static final ModConfigSpec.DoubleValue PRAYER_CUSTOM_ISHA_ANGLE;
    public static final ModConfigSpec.ConfigValue<String> PRAYER_ASR_METHOD;
    public static final ModConfigSpec.ConfigValue<String> PRAYER_HIGH_LATITUDE_RULE;

    public static final ModConfigSpec.IntValue PRAYER_ADJUST_FAJR;
    public static final ModConfigSpec.IntValue PRAYER_ADJUST_SUNRISE;
    public static final ModConfigSpec.IntValue PRAYER_ADJUST_DHUHR;
    public static final ModConfigSpec.IntValue PRAYER_ADJUST_ASR;
    public static final ModConfigSpec.IntValue PRAYER_ADJUST_MAGHRIB;
    public static final ModConfigSpec.IntValue PRAYER_ADJUST_ISHA;

    public static final ModConfigSpec.BooleanValue PRAYER_SALAH_HUD_ENABLED;
    public static final ModConfigSpec.BooleanValue PRAYER_NOTIFICATIONS_ENABLED;
    public static final ModConfigSpec.BooleanValue PRAYER_ADVANCE_NOTIFICATION_ENABLED;
    public static final ModConfigSpec.IntValue PRAYER_ADVANCE_NOTIFICATION_MINUTES;
    public static final ModConfigSpec.BooleanValue PRAYER_START_NOTIFICATION_ENABLED;

    public static final ModConfigSpec.BooleanValue PRAYER_NOTIFY_FAJR;
    public static final ModConfigSpec.BooleanValue PRAYER_NOTIFY_DHUHR;
    public static final ModConfigSpec.BooleanValue PRAYER_NOTIFY_ASR;
    public static final ModConfigSpec.BooleanValue PRAYER_NOTIFY_MAGHRIB;
    public static final ModConfigSpec.BooleanValue PRAYER_NOTIFY_ISHA;

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

        builder.comment("Qibla direction and client-side observer location").push("qibla");

        QIBLA_ENABLED = builder
                .comment("Master switch for Qibla direction feature")
                .define("enabled", true);

        QIBLA_LOCATION_CONFIGURED = builder
                .comment("Explicit flag indicating whether real-world observer coordinates have been manually configured.",
                        "Coordinates are strictly client-side and never transmitted over the network.")
                .define("location_configured", false);

        QIBLA_LATITUDE = builder
                .comment("Observer latitude in decimal degrees (-90.0 to +90.0). Positive = North, Negative = South.")
                .defineInRange("latitude", 0.0, -90.0, 90.0);

        QIBLA_LONGITUDE = builder
                .comment("Observer longitude in decimal degrees (-180.0 to +180.0). Positive = East, Negative = West.")
                .defineInRange("longitude", 0.0, -180.0, 180.0);

        QIBLA_HUD_ENABLED = builder
                .comment("Show Qibla direction indicator on the HUD")
                .define("hud_enabled", true);

        builder.pop();

        builder.comment("Prayer time calculation settings (client-only, offline, reuses qibla observer coordinates)").push("prayer");

        PRAYER_ENABLED = builder
                .comment("Master switch for daily prayer time calculation")
                .define("enabled", true);

        PRAYER_ZONE_ID = builder
                .comment("Explicit IANA time zone ID (e.g., 'Europe/London', 'America/New_York', 'Asia/Tokyo').",
                        "Leave blank ('') to use the operating system's default time zone.")
                .define("zone_id", "");

        PRAYER_CALCULATION_METHOD = builder
                .comment("Prayer calculation preset: MUSLIM_WORLD_LEAGUE, EGYPTIAN, KARACHI, NORTH_AMERICA, KUWAIT, SINGAPORE, DUBAI, or CUSTOM.")
                .define("calculation_method", "MUSLIM_WORLD_LEAGUE");

        PRAYER_CUSTOM_FAJR_ANGLE = builder
                .comment("Custom solar depression angle for Fajr in degrees (1.0 to 30.0), used when calculation_method is CUSTOM.")
                .defineInRange("custom_fajr_angle", 18.0, 1.0, 30.0);

        PRAYER_CUSTOM_ISHA_ANGLE = builder
                .comment("Custom solar depression angle for Isha in degrees (1.0 to 30.0), used when calculation_method is CUSTOM.")
                .defineInRange("custom_isha_angle", 17.0, 1.0, 30.0);

        PRAYER_ASR_METHOD = builder
                .comment("Asr juristic shadow-factor method: STANDARD (shadow factor 1) or HANAFI (shadow factor 2).")
                .define("asr_method", "STANDARD");

        PRAYER_HIGH_LATITUDE_RULE = builder
                .comment("High-latitude twilight fallback rule: NONE, MIDDLE_OF_NIGHT, SEVENTH_OF_NIGHT, or TWILIGHT_ANGLE.")
                .define("high_latitude_rule", "MIDDLE_OF_NIGHT");

        PRAYER_ADJUST_FAJR = builder
                .comment("Minute offset adjustment for Fajr (-60 to +60)")
                .defineInRange("fajr_adjustment_minutes", 0, -60, 60);

        PRAYER_ADJUST_SUNRISE = builder
                .comment("Minute offset adjustment for Sunrise (-60 to +60)")
                .defineInRange("sunrise_adjustment_minutes", 0, -60, 60);

        PRAYER_ADJUST_DHUHR = builder
                .comment("Minute offset adjustment for Dhuhr (-60 to +60)")
                .defineInRange("dhuhr_adjustment_minutes", 0, -60, 60);

        PRAYER_ADJUST_ASR = builder
                .comment("Minute offset adjustment for Asr (-60 to +60)")
                .defineInRange("asr_adjustment_minutes", 0, -60, 60);

        PRAYER_ADJUST_MAGHRIB = builder
                .comment("Minute offset adjustment for Maghrib (-60 to +60)")
                .defineInRange("maghrib_adjustment_minutes", 0, -60, 60);

        PRAYER_ADJUST_ISHA = builder
                .comment("Minute offset adjustment for Isha (-60 to +60)")
                .defineInRange("isha_adjustment_minutes", 0, -60, 60);

        PRAYER_SALAH_HUD_ENABLED = builder
                .comment("Show next obligatory prayer and countdown on the client HUD")
                .define("salah_hud_enabled", true);

        PRAYER_NOTIFICATIONS_ENABLED = builder
                .comment("Master switch for client-side Salah reminder Toast notifications")
                .define("notifications_enabled", true);

        PRAYER_ADVANCE_NOTIFICATION_ENABLED = builder
                .comment("Enable advance reminder notification before an obligatory prayer begins")
                .define("advance_notification_enabled", true);

        PRAYER_ADVANCE_NOTIFICATION_MINUTES = builder
                .comment("Minutes before an obligatory prayer to show the advance reminder (0 to 60; 0 = no advance notification)")
                .defineInRange("advance_notification_minutes", 10, 0, 60);

        PRAYER_START_NOTIFICATION_ENABLED = builder
                .comment("Enable reminder notification when an obligatory prayer start time is reached")
                .define("start_notification_enabled", true);

        PRAYER_NOTIFY_FAJR = builder
                .comment("Enable Salah notifications for Fajr")
                .define("notify_fajr", true);

        PRAYER_NOTIFY_DHUHR = builder
                .comment("Enable Salah notifications for Dhuhr")
                .define("notify_dhuhr", true);

        PRAYER_NOTIFY_ASR = builder
                .comment("Enable Salah notifications for Asr")
                .define("notify_asr", true);

        PRAYER_NOTIFY_MAGHRIB = builder
                .comment("Enable Salah notifications for Maghrib")
                .define("notify_maghrib", true);

        PRAYER_NOTIFY_ISHA = builder
                .comment("Enable Salah notifications for Isha")
                .define("notify_isha", true);

        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {}
}
