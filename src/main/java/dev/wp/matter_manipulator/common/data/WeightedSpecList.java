package dev.wp.matter_manipulator.common.data;

import dev.wp.matter_manipulator.common.building.BlockSpec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public class WeightedSpecList {
    public final List<WeightedEntry> entries = new ArrayList<>();

    public WeightedSpecList() {}

    public void add(BlockSpec spec, int weight) {
        entries.add(new WeightedEntry(spec, weight));
    }

    public void clear() {
        entries.clear();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Returns true if any entry has the same block as {@code target} (ignores state properties). */
    public boolean containsBlock(BlockSpec target) {
        for (var e : entries) {
            if (e.spec.state.getBlock() == target.state.getBlock()) return true;
        }
        return false;
    }

    /** Returns a random spec based on weights, or null if empty. */
    @Nullable
    public BlockSpec pick(RandomSource rng) {
        if (entries.isEmpty()) return null;
        int total = entries.stream().mapToInt(e -> e.weight).sum();
        if (total <= 0) return entries.get(0).spec;
        int roll = rng.nextInt(total);
        int cumulative = 0;
        for (WeightedEntry e : entries) {
            cumulative += e.weight;
            if (roll < cumulative) return e.spec;
        }
        return entries.get(entries.size() - 1).spec;
    }

    /** Returns the first spec, or stone if empty (fallback). */
    public BlockState pickState(RandomSource rng) {
        BlockSpec spec = pick(rng);
        if (spec != null) return spec.state;
        return Blocks.STONE.defaultBlockState();
    }

    public boolean contains(Block block) {
        return entries.stream().anyMatch(e -> e.spec.state.getBlock() == block);
    }

    public CompoundTag toNBT() {
        var list = new ListTag();
        for (var e : entries) {
            var tag = new CompoundTag();
            tag.put("spec", e.spec.toNBT());
            tag.putInt("weight", e.weight);
            list.add(tag);
        }
        var out = new CompoundTag();
        out.put("entries", list);
        return out;
    }

    public static WeightedSpecList fromNBT(CompoundTag tag) {
        var list = new WeightedSpecList();
        for (var e : tag.getList("entries", Tag.TAG_COMPOUND)) {
            var ct = (CompoundTag) e;
            list.add(BlockSpec.fromNBT(ct.getCompound("spec")), ct.getInt("weight"));
        }
        return list;
    }

    public static class WeightedEntry {
        public final BlockSpec spec;
        public final int weight;

        public WeightedEntry(BlockSpec spec, int weight) {
            this.spec = spec;
            this.weight = weight;
        }
    }
}
