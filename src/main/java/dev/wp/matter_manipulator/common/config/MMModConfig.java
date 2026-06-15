package dev.wp.matter_manipulator.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class MMModConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<Boolean> PASTE_AUTO_CLEAR;
    public static final ForgeConfigSpec.ConfigValue<Boolean> RESET_TRANSFORM;
    public static final ForgeConfigSpec.ConfigValue<Integer> MAX_HINTS;
    public static final ForgeConfigSpec.ConfigValue<Integer> STATUS_EXPIRATION;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HINTS_ON_TOP;
    public static final ForgeConfigSpec.ConfigValue<Boolean> DEBUG_LOGGING;
    public static final ForgeConfigSpec.ConfigValue<Integer> MK3_PLACE_SPEED;
    public static final ForgeConfigSpec.ConfigValue<Double> AE_FE_PER_AE;
    public static final ForgeConfigSpec.ConfigValue<Double> AE_FE_PER_EU;

    static {
        BUILDER.push("interaction");
        PASTE_AUTO_CLEAR = BUILDER.comment("Clear the paste region when the copy or cut regions are marked")
                .define("pasteAutoClear", true);
        RESET_TRANSFORM = BUILDER.comment(
                        "Clear the transform and the stacking amount when the coordinates are cleared")
                .define("resetTransform", true);
        BUILDER.pop();

        BUILDER.push("rendering");
        MAX_HINTS = BUILDER.comment(
                        "Max blocks shown in the preview. 0 = unlimited. Very large previews use a lot of VRAM and "
                                + "can stutter or crash the client; lower this if needed. Client only.")
                .defineInRange("maxHints", 1_000_000, 0, Integer.MAX_VALUE);
        STATUS_EXPIRATION = BUILDER.comment(
                        "Controls the duration of the build status warning/error hints (seconds). Client only. Set to 0 to never clear hints.")
                .defineInRange("statusExpiration", 60, 0, 3600);
        HINTS_ON_TOP = BUILDER.comment("When true, hints will always be drawn on top of the terrain. Client only.")
                .define("hintsOnTop", true);
        BUILDER.pop();

        BUILDER.push("debug");
        DEBUG_LOGGING = BUILDER.define("enableDebugLogging", false);
        BUILDER.pop();

        BUILDER.push("building");
        MK3_PLACE_SPEED = BUILDER.comment("Blocks per tick. High values may cause world desync and lag. Server only.")
                .defineInRange("mk3PlaceSpeed", 256, 1, 4096);
        BUILDER.pop();

        BUILDER.push("uplink");
        AE_FE_PER_AE = BUILDER.comment(
                        "Forge Energy per AE2 energy unit when drawing the manipulator's power from a bound AE2 network (Power P2P upgrade).")
                .defineInRange("fePerAe", 2.0, 0.0001, 1_000_000.0);
        AE_FE_PER_EU = BUILDER.comment(
                        "Forge Energy per EU, used to convert AE network power into the manipulator's EU buffer when GregTech is present.")
                .defineInRange("fePerEu", 4.0, 0.0001, 1_000_000.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
