package dev.wp.matter_manipulator.common.items.manipulator;

public enum ShapeType {
    LINE,
    CUBE,
    SPHERE,
    CYLINDER;

    public boolean requiresC() {
        return this == CYLINDER;
    }
}
