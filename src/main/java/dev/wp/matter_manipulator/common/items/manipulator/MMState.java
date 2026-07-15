package dev.wp.matter_manipulator.common.items.manipulator;

import dev.wp.matter_manipulator.MMMod;
import dev.wp.matter_manipulator.common.building.BlockSpec;
import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.config.MMModConfig;
import dev.wp.matter_manipulator.common.data.WeightedSpecList;
import dev.wp.matter_manipulator.common.items.MMUpgrades;
import dev.wp.matter_manipulator.common.persist.PersistAdapters;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.BitSet;

import javax.annotation.Nullable;

public class MMState {
    private static final int JSON_VERSION = 1;
    private static final String KEY_CHARGE = "charge";
    private static final String KEY_UPGRADES = "upgrades";
    private static final String KEY_CONFIG = "config";
    private static final String KEY_JSON_VER = "jsonVersion";

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(BlockSpec.class, PersistAdapters.BLOCK_SPEC_SERIALIZER)
            .registerTypeAdapter(BlockSpec.class, PersistAdapters.BLOCK_SPEC_DESERIALIZER)
            .registerTypeAdapter(Location.class, PersistAdapters.LOCATION_SERIALIZER)
            .registerTypeAdapter(Location.class, PersistAdapters.LOCATION_DESERIALIZER)
            .registerTypeAdapter(WeightedSpecList.class, PersistAdapters.WEIGHTED_LIST_SERIALIZER)
            .registerTypeAdapter(WeightedSpecList.class, PersistAdapters.WEIGHTED_LIST_DESERIALIZER)
            .create();

    private final ItemStack stack;
    private MMConfig config;
    private boolean configDirty;

    public MMState(ItemStack stack) {
        this.stack = stack;
        ensureTag();
    }

    private CompoundTag tag() {
        return stack.getOrCreateTag();
    }

    private void ensureTag() {
        var t = tag();
        if (!t.contains(KEY_JSON_VER)) {
            t.putInt(KEY_JSON_VER, JSON_VERSION);
        }
    }

    // ── Energy ──────────────────────────────────────────────────────────────────

    public long getCharge() {
        return tag().getLong(KEY_CHARGE);
    }

    public void setCharge(long charge) {
        tag().putLong(KEY_CHARGE, charge);
    }

    public long consumeCharge(long amount) {
        long current = getCharge();
        long actual = Math.min(current, amount);
        setCharge(current - actual);
        return actual;
    }

    public boolean hasCharge(long amount) {
        return getCharge() >= amount;
    }

    // ── Upgrades ─────────────────────────────────────────────────────────────────

    public BitSet getUpgrades() {
        byte[] bytes = tag().getByteArray(KEY_UPGRADES);
        return bytes.length == 0 ? new BitSet() : BitSet.valueOf(bytes);
    }

    public void setUpgrades(BitSet upgrades) {
        tag().putByteArray(KEY_UPGRADES, upgrades.toByteArray());
    }

    public boolean hasUpgrade(int upgradeId) {
        return getUpgrades().get(upgradeId);
    }

    public boolean hasCapability(MMTier tier, int cap) {
        int c = tier.capabilities;
        for (var u : MMUpgrades.values()) {
            if (hasUpgrade(u.bit)) {
                c |= u.providesCaps;
            }
        }
        return (c & cap) != 0;
    }

    public int getPlaceSpeed(MMTier tier) {
        int speed = (tier == MMTier.MK3) ? MMModConfig.MK3_PLACE_SPEED.get() : tier.placeSpeed;
        if (hasUpgrade(MMUpgrades.SPEED.bit)) {
            speed *= 2;
        }
        return speed;
    }

    // ── Config ───────────────────────────────────────────────────────────────────

    public MMConfig getConfig() {
        if (config == null) {
            config = loadConfig();
        }
        return config;
    }

    public void markConfigDirty() {
        configDirty = true;
    }

    public void saveIfDirty() {
        if (configDirty && config != null) {
            saveConfig(config);
            configDirty = false;
        }
    }

    private MMConfig loadConfig() {
        var t = tag();
        if (!t.contains(KEY_CONFIG)) return new MMConfig();
        try {
            return GSON.fromJson(t.getString(KEY_CONFIG), MMConfig.class);
        } catch (Exception e) {
            MMMod.LOGGER.warn("Failed to load MMConfig from NBT, resetting: {}", e.getMessage());
            return new MMConfig();
        }
    }

    public void saveConfig(MMConfig cfg) {
        tag().putString(KEY_CONFIG, GSON.toJson(cfg));
    }

    /** Convenience: get config, apply a mutation, save, and mark dirty. */
    public MMConfig mutate() {
        config = getConfig();
        configDirty = true;
        return config;
    }

    // ── Static factory ──────────────────────────────────────────────────────────

    public static MMState of(ItemStack stack) {
        return new MMState(stack);
    }

    @Nullable
    public static MMState ofHeld(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof ItemMatterManipulator)) return null;
        return new MMState(stack);
    }
}
