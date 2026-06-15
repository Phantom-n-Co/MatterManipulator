package dev.wp.matter_manipulator.common.utils;

import dev.wp.matter_manipulator.common.compat.AECompat;
import dev.wp.matter_manipulator.common.compat.GTCompat;
import net.minecraftforge.fml.ModList;

/**
 * Presence flags for soft-dependency mods, resolved once. Guard every call into {@link GTCompat} or
 * {@link AECompat} with the matching flag (short-circuited with {@code &&}) so those classes — and
 * the mod classes they import — are only loaded when the mod is actually installed.
 */
public final class Mods {

    public static final boolean GTCEU = ModList.get().isLoaded("gtceu");
    public static final boolean AE2 = ModList.get().isLoaded("ae2");

    private Mods() {}
}
