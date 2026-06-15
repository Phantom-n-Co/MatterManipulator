package dev.wp.matter_manipulator.common.compat.ae;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.util.IConfigManager;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;

import java.util.EnumSet;

/**
 * AE2 grid device that hosts a {@link UplinkLogic}. It joins the network through ME cables, exposes
 * the network (storage, energy) to a bound Matter Manipulator, and acts as a pattern provider for
 * the manipulator's plan patterns.
 *
 * <p>All AE-bridge behavior lives in {@link UplinkLogic}; this class is just one host for it.
 * {@link PatternProviderLogic} self-registers the crafting-provider and ticker services, so this
 * host adds none of its own. Modeled on SpatialToolsCompatible's {@code CraftingBufferBlockEntity}.
 */
@Getter
public class UplinkBlockEntity extends AENetworkBlockEntity implements PatternProviderLogicHost, ICraftingRequester {
    private final UplinkLogic uplink;

    public UplinkBlockEntity(BlockPos pos, BlockState state) {
        super(AEUplinkRegistrar.UPLINK_BE_TYPE.get(), pos, state);

        this.uplink = new UplinkLogic(getMainNode(), this, this, this::setChanged);

        getMainNode()
                .setIdlePowerUsage(2.0)
                .addService(ICraftingRequester.class, this)
                .setVisualRepresentation(
                        new ItemStack(AEUplinkRegistrar.UPLINK_BLOCK.get().asItem()));
    }

    public IGrid getGrid() {
        return getMainNode().getGrid();
    }

    /** Driven by the block's server ticker: processes deferred plan-token emit/void. */
    public void serverTick() {
        uplink.tick();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            uplink.onLoad();
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("uplink", uplink.toTag());
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        if (tag.contains("uplink", Tag.TAG_COMPOUND)) {
            uplink.fromTag(tag.getCompound("uplink"));
        }
    }

    // ── PatternProviderLogicHost ───────────────────────────────────────────────────

    @Override
    public EnumSet<Direction> getTargets() {
        // pushPattern is handled directly by UplinkPatternLogic; no adjacent push targets.
        return EnumSet.noneOf(Direction.class);
    }

    @Override
    public PatternProviderLogic getLogic() {
        return uplink.getLogic();
    }

    @Override
    public BlockEntity getBlockEntity() {
        return this;
    }

    @Override
    public void saveChanges() {
        setChanged();
    }

    @Override
    public IConfigManager getConfigManager() {
        return uplink.getLogic().getConfigManager();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(AEUplinkRegistrar.UPLINK_BLOCK.get().asItem());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(AEUplinkRegistrar.UPLINK_BLOCK.get().asItem());
    }

    // ── ICraftingRequester (auto plans) ────────────────────────────────────────────

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return uplink.getRequestedJobs();
    }

    @Override
    public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        return uplink.insertCraftedItems(what, amount, mode);
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        uplink.jobStateChange(link);
    }
}
