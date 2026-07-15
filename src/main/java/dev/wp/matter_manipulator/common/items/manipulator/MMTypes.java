package dev.wp.matter_manipulator.common.items.manipulator;

public final class MMTypes {
    private MMTypes() {}

    public enum RemoveMode {
        NONE,
        REPLACEABLE,
        ALL
    }

    public enum SlotType {
        CORNER,
        EDGE,
        FACE,
        VOLUME,
        ALL
    }

    public enum ShapeType {
        LINE,
        CUBE,
        SPHERE,
        CYLINDER;

        public boolean requiresC() {
            return this == CYLINDER;
        }
    }

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

    public enum PendingAction {
        GEOM_SELECTING_BLOCK,
        MARK_COPY_A,
        MARK_COPY_B,
        MARK_CUT_A,
        MARK_CUT_B,
        MARK_PASTE_A,
        MARK_PASTE_B,
        EXCH_ADD_REPLACE,
        EXCH_SET_REPLACE,
        EXCH_SET_TARGET,
        PICK_CABLE,
        SELECTING_CORNER_B,
        MOVING_COORDS,
        MARK_ARRAY;

        public boolean isDirectBlockPick() {
            return this == GEOM_SELECTING_BLOCK
                    || this == EXCH_ADD_REPLACE
                    || this == EXCH_SET_REPLACE
                    || this == EXCH_SET_TARGET
                    || this == PICK_CABLE;
        }
    }

    public static final class MMCapability {
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
}
