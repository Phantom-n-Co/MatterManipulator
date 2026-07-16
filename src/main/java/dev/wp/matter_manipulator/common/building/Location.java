package dev.wp.matter_manipulator.common.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;

public class Location {
    public ResourceKey<Level> dimension;
    public BlockPos pos;

    public Location(ResourceKey<Level> dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos;
    }

    public Location(BlockPos pos) {
        this(Level.OVERWORLD, pos);
    }

    public Location(int x, int y, int z) {
        this(Level.OVERWORLD, new BlockPos(x, y, z));
    }

    public CompoundTag toNBT() {
        var tag = new CompoundTag();
        tag.putString("dim", dimension.location().toString());
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    public static Location fromNBT(CompoundTag tag) {
        var dimKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dim")));
        return new Location(dimKey, new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location other)) return false;
        return Objects.equals(dimension, other.dimension) && Objects.equals(pos, other.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, pos);
    }

    @Override
    public String toString() {
        return String.format("[%s %d,%d,%d]", dimension.location(), pos.getX(), pos.getY(), pos.getZ());
    }
}
