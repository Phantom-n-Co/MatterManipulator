package dev.wp.matter_manipulator.common.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

import javax.annotation.Nullable;

public class SimulatedLevelReader implements LevelReader {
    private final LevelReader delegate;
    private final BlockPos airPos;

    public SimulatedLevelReader(LevelReader delegate, BlockPos airPos) {
        this.delegate = delegate;
        this.airPos = airPos;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (pos.equals(airPos)) return Blocks.AIR.defaultBlockState();
        return delegate.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (pos.equals(airPos)) return Fluids.EMPTY.defaultFluidState();
        return delegate.getFluidState(pos);
    }

    @Override
    public boolean isClientSide() {
        return delegate.isClientSide();
    }

    @Override
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public DimensionType dimensionType() {
        return delegate.dimensionType();
    }

    @Override
    public float getShade(Direction direction, boolean b) {
        return delegate.getShade(direction, b);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos blockPos) {
        if (blockPos.equals(airPos)) return null;
        return delegate.getBlockEntity(blockPos);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return delegate.getWorldBorder();
    }

    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB aabb) {
        return delegate.getEntityCollisions(entity, aabb);
    }

    @Override
    public RegistryAccess registryAccess() {
        return delegate.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return delegate.enabledFeatures();
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int i, int i1, int i2) {
        return delegate.getUncachedNoiseBiome(i, i1, i2);
    }

    @Nullable
    @Override
    public ChunkAccess getChunk(int i, int i1, ChunkStatus chunkStatus, boolean b) {
        return delegate.getChunk(i, i1, chunkStatus, b);
    }

    @Override
    public boolean hasChunk(int i, int i1) {
        return delegate.hasChunk(i, i1);
    }

    @Override
    public int getHeight(Heightmap.Types types, int i, int i1) {
        return delegate.getHeight(types, i, i1);
    }

    @Override
    public int getSkyDarken() {
        return delegate.getSkyDarken();
    }

    @Override
    public BiomeManager getBiomeManager() {
        return delegate.getBiomeManager();
    }

    @Override
    public boolean isWaterAt(BlockPos blockPos) {
        if (blockPos.equals(airPos)) return false;
        return delegate.isWaterAt(blockPos);
    }

    @Override
    public int getDirectSignal(BlockPos blockPos, Direction direction) {
        return delegate.getDirectSignal(blockPos, direction);
    }

    @Override
    public int getMaxLightLevel() {
        return delegate.getMaxLightLevel();
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return delegate.getMinBuildHeight();
    }
}
