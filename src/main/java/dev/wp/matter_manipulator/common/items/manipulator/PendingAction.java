package dev.wp.matter_manipulator.common.items.manipulator;

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
    /**
     * Waiting for the user to right-click to set corner B after corner A was anchored.
     */
    SELECTING_CORNER_B,
    /**
     * Coordinates with non-null offsets in MMConfig track the crosshair until the next
     * right-click commits them.
     */
    MOVING_COORDS,
    /**
     * The next right-click sets the copy array span from the looked-at position.
     */
    MARK_ARRAY;

    public boolean isDirectBlockPick() {
        return this == GEOM_SELECTING_BLOCK
                || this == EXCH_ADD_REPLACE
                || this == EXCH_SET_REPLACE
                || this == EXCH_SET_TARGET
                || this == PICK_CABLE;
    }
}
