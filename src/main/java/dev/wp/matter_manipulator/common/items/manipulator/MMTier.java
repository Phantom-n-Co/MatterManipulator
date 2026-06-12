package dev.wp.matter_manipulator.common.items.manipulator;

import dev.wp.matter_manipulator.common.config.MMModConfig;

public enum MMTier {

    PROTO(
            "proto",
            MMModConfig.PROTO_RANGE.get(),
            MMModConfig.PROTO_PLACE_SPEED.get(),
            10_000_000L,
            MMCapability.ALLOW_GEOMETRY
    ),
    MK1(
            "mk1",
            MMModConfig.MK1_RANGE.get(),
            MMModConfig.MK1_PLACE_SPEED.get(),
            100_000_000L,
            MMCapability.ALLOW_GEOMETRY | MMCapability.ALLOW_REMOVING | MMCapability.ALLOW_EXCHANGING
                    | MMCapability.ALLOW_CONFIGURING | MMCapability.ALLOW_CABLES | MMCapability.CONNECTS_TO_AE
    ),
    MK2(
            "mk2",
            MMModConfig.MK2_RANGE.get(),
            MMModConfig.MK2_PLACE_SPEED.get(),
            1_000_000_000L,
            MMCapability.ALLOW_GEOMETRY | MMCapability.ALLOW_REMOVING | MMCapability.ALLOW_EXCHANGING
                    | MMCapability.ALLOW_CONFIGURING | MMCapability.ALLOW_CABLES | MMCapability.CONNECTS_TO_AE
                    | MMCapability.ALLOW_COPYING | MMCapability.ALLOW_MOVING
    ),
    MK3(
            "mk3",
            MMModConfig.MK3_RANGE.get(),
            MMModConfig.MK3_PLACE_SPEED.get(),
            10_000_000_000L,
            MMCapability.ALLOW_GEOMETRY | MMCapability.ALLOW_REMOVING | MMCapability.ALLOW_EXCHANGING
                    | MMCapability.ALLOW_CONFIGURING | MMCapability.ALLOW_CABLES | MMCapability.CONNECTS_TO_AE
                    | MMCapability.ALLOW_COPYING | MMCapability.ALLOW_MOVING | MMCapability.CONNECTS_TO_UPLINK
    );

    public final String id;
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

    MMTier(String id, int range, int placeSpeed, long maxEnergy, int capabilities) {
        this.id = id;
        this.range = range;
        this.placeSpeed = placeSpeed;
        this.maxEnergy = maxEnergy;
        this.capabilities = capabilities;
    }

    public boolean hasCapability(int cap) {
        return (capabilities & cap) != 0;
    }
}
