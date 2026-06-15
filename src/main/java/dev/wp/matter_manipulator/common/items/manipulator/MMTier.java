package dev.wp.matter_manipulator.common.items.manipulator;

import dev.wp.matter_manipulator.common.utils.Mods;

public enum MMTier {

    PROTO(
            "proto",
            11,
            32,
            16,
            10_000_000L,
            MMCapability.ALLOW_GEOMETRY
    ),
    MK1(
            "mk1",
            11,
            64,
            64,
            100_000_000L,
            MMCapability.ALLOW_GEOMETRY | MMCapability.ALLOW_REMOVING | MMCapability.ALLOW_EXCHANGING
                    | MMCapability.ALLOW_CONFIGURING | MMCapability.ALLOW_CABLES | MMCapability.CONNECTS_TO_AE
    ),
    MK2(
            "mk2",
            11,
            128,
            256,
            1_000_000_000L,
            MMCapability.ALLOW_GEOMETRY | MMCapability.ALLOW_REMOVING | MMCapability.ALLOW_EXCHANGING
                    | MMCapability.ALLOW_CONFIGURING | MMCapability.ALLOW_CABLES | MMCapability.CONNECTS_TO_AE
                    | MMCapability.ALLOW_COPYING | MMCapability.ALLOW_MOVING
    ),
    MK3(
            "mk3",
            11,
            -1,
            256,
            10_000_000_000L,
            MMCapability.ALLOW_GEOMETRY | MMCapability.ALLOW_REMOVING | MMCapability.ALLOW_EXCHANGING
                    | MMCapability.ALLOW_CONFIGURING | MMCapability.ALLOW_CABLES | MMCapability.CONNECTS_TO_AE
                    | MMCapability.ALLOW_COPYING | MMCapability.ALLOW_MOVING | MMCapability.CONNECTS_TO_UPLINK
    );

    public final String id;

    public final int euTier;
    /**
     * Block range (-1 = unlimited).
     */
    public final int range;
    /**
     * Blocks placed per tick.
     */
    public final int placeSpeed;
    /**
     * Maximum Energy (FE/EU depending on if GTm is present) stored.
     */
    public final long maxEnergy;
    /**
     * Capability bitmask.
     */
    public final int capabilities;

    MMTier(String id, int euTier, int range, int placeSpeed, long maxEnergy, int capabilities) {
        this.id = id;
        this.euTier = euTier;
        this.range = range;
        this.placeSpeed = placeSpeed;
        this.maxEnergy = maxEnergy;
        this.capabilities = capabilities;
    }

    public boolean hasCapability(int cap) {
        return (capabilities & cap) != 0;
    }

    /**
     * Effective energy capacity for the unit in use: full {@code maxEnergy} (EU) when GregTech is
     * present, otherwise capped at {@link Integer#MAX_VALUE} since Forge Energy (FE) is int-based.
     */
    public long effectiveMaxEnergy() {
        return Mods.GTCEU ? maxEnergy : Math.min(maxEnergy, Integer.MAX_VALUE);
    }

    /** GregTech voltage tier used for charging as an electric item (LV, MV, HV, EV by MK level). */
    public int euTier() {
        return this.euTier;
    }

    /** Whether energy is measured in EU (GregTech present) rather than FE. */
    public static boolean usesEU() {
        return Mods.GTCEU;
    }
}
