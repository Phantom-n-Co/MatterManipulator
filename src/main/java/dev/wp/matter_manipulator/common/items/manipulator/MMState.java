package dev.wp.matter_manipulator.common.items.manipulator;

import dev.wp.matter_manipulator.common.config.MMModConfig;
import dev.wp.matter_manipulator.common.items.MMUpgrades;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.BitSet;

import javax.annotation.Nullable;

public class MMState {
    private static final String KEY_CHARGE = "charge";
    private static final String KEY_UPGRADES = "upgrades";
    private static final String KEY_CONFIG = "config";

    private final ItemStack stack;
    private MMConfig config;
    private boolean configDirty;

    public MMState(ItemStack stack) {
        this.stack = stack;
    }

    private CompoundTag tag() {
        return stack.getOrCreateTag();
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
        return MMConfig.fromNBT(t.getCompound(KEY_CONFIG));
    }

    public void saveConfig(MMConfig cfg) {
        tag().put(KEY_CONFIG, cfg.toNBT());
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
