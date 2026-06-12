package dev.wp.matter_manipulator.common.items.manipulator;

public enum MMTier {

    PROTO(
        "proto",
        32,
        16,
        10_000_000L,
        MMCapability.ALLOW_GEOMETRY
    ),
    MK1(
        "mk1",
        64,
        64,
        100_000_000L,
        MMCapability.ALLOW_GEOMETRY | MMCapability.ALLOW_REMOVING | MMCapability.ALLOW_EXCHANGING
            | MMCapability.ALLOW_CONFIGURING | MMCapability.ALLOW_CABLES | MMCapability.CONNECTS_TO_AE
    ),
    MK2(
        "mk2",
        128,
        256,
        1_000_000_000L,
        MMCapability.ALLOW_GEOMETRY | MMCapability.ALLOW_REMOVING | MMCapability.ALLOW_EXCHANGING
            | MMCapability.ALLOW_CONFIGURING | MMCapability.ALLOW_CABLES | MMCapability.CONNECTS_TO_AE
            | MMCapability.ALLOW_COPYING | MMCapability.ALLOW_MOVING
    ),
    MK3(
        "mk3",
        -1,
        256,
        10_000_000_000L,
        MMCapability.ALLOW_GEOMETRY | MMCapability.ALLOW_REMOVING | MMCapability.ALLOW_EXCHANGING
            | MMCapability.ALLOW_CONFIGURING | MMCapability.ALLOW_CABLES | MMCapability.CONNECTS_TO_AE
            | MMCapability.ALLOW_COPYING | MMCapability.ALLOW_MOVING | MMCapability.CONNECTS_TO_UPLINK
    );

    public final String id;
    /** Block range (-1 = unlimited). */
    public final int range;
    /** Blocks placed per tick. */
    public final int placeSpeed;
    /** Maximum Energy (FE/EU depending on if GTm is present) stored. */
    public final long maxEnergy;
    /** Capability bitmask. */
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
