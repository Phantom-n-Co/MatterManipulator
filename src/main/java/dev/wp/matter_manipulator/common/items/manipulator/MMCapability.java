package dev.wp.matter_manipulator.common.items.manipulator;

public final class MMCapability {
    private MMCapability() {}

    public static final int CONNECTS_TO_AE = 1;
    public static final int CONNECTS_TO_UPLINK = 1 << 1;
    public static final int ALLOW_REMOVING = 1 << 2;
    public static final int ALLOW_GEOMETRY = 1 << 3;
    public static final int ALLOW_CONFIGURING = 1 << 4;
    public static final int ALLOW_COPYING = 1 << 5;
    public static final int ALLOW_EXCHANGING = 1 << 6;
    public static final int ALLOW_MOVING = 1 << 7;
    public static final int ALLOW_CABLES = 1 << 8;
}
