package dev.wp.matter_manipulator.common.items.manipulator;

public enum PlaceMode {
    GEOMETRY,
    COPYING,
    MOVING,
    EXCHANGING,
    CABLES;

    public boolean isCopyOrMove() {
        return this == COPYING || this == MOVING;
    }
}
