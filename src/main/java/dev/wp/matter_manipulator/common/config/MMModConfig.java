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
    public static final ForgeConfigSpec.ConfigValue<Integer> PROTO_RANGE;
    public static final ForgeConfigSpec.ConfigValue<Integer> MK1_RANGE;
    public static final ForgeConfigSpec.ConfigValue<Integer> MK2_RANGE;
    public static final ForgeConfigSpec.ConfigValue<Integer> MK3_RANGE;
    public static final ForgeConfigSpec.ConfigValue<Integer> PROTO_PLACE_SPEED;
    public static final ForgeConfigSpec.ConfigValue<Integer> MK1_PLACE_SPEED;
    public static final ForgeConfigSpec.ConfigValue<Integer> MK2_PLACE_SPEED;
    public static final ForgeConfigSpec.ConfigValue<Integer> MK3_PLACE_SPEED;

    static {
        BUILDER.push("interaction");
        PASTE_AUTO_CLEAR = BUILDER
                .comment("Clear the paste region when the copy or cut regions are marked")
                .define("pasteAutoClear", true);
        RESET_TRANSFORM = BUILDER
                .comment("Clear the transform and the stacking amount when the coordinates are cleared")
                .define("resetTransform", true);
        BUILDER.pop();

        BUILDER.push("rendering");
        MAX_HINTS = BUILDER
                .comment("Controls how many blocks are shown in the preview. Client only.")
                .define("maxHints", 1_000_000);
        STATUS_EXPIRATION = BUILDER
                .comment("Controls the duration of the build status warning/error hints (seconds). Client only. Set to 0 to never clear hints.")
                .define("statusExpiration", 60);
        HINTS_ON_TOP = BUILDER
                .comment("When true, hints will always be drawn on top of the terrain. Client only.")
                .define("hintsOnTop", true);
        BUILDER.pop();

        BUILDER.push("debug");
        DEBUG_LOGGING = BUILDER
                .define("enableDebugLogging", false);
        BUILDER.pop();

        BUILDER.push("building");
        BUILDER.push("range").comment("Build range (-1 = unlimited). Server only.");
        PROTO_RANGE = BUILDER
                .defineInRange("protoRange", 32, -1, Integer.MAX_VALUE);
        MK1_RANGE = BUILDER
                .defineInRange("mk1Range", 64, -1, Integer.MAX_VALUE);
        MK2_RANGE = BUILDER
                .defineInRange("mk2Range", 128, -1, Integer.MAX_VALUE);
        MK3_RANGE = BUILDER
                .defineInRange("mk3Range", -1, -1, Integer.MAX_VALUE);
        BUILDER.push("build-speed")
                .comment("Blocks per tick. High values may cause world desync and lag. Server only.");
        PROTO_PLACE_SPEED = BUILDER
                .defineInRange("protoPlaceSpeed", 128, 1, 4096);
        MK1_PLACE_SPEED = BUILDER
                .defineInRange("mk1PlaceSpeed", 128, 1, 4096);
        MK2_PLACE_SPEED = BUILDER
                .defineInRange("mk2PlaceSpeed", 256, 1, 4096);
        MK3_PLACE_SPEED = BUILDER
                .defineInRange("mk3PlaceSpeed", 256, 1, 4096);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
