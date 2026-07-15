package dev.wp.matter_manipulator.common.building.shapes;

import dev.wp.matter_manipulator.common.items.manipulator.MMTypes.SlotType;

import net.minecraft.core.BlockPos;

public record ShapeBlock(BlockPos pos, SlotType slot) {}
