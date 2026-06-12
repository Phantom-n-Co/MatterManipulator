package dev.wp.matter_manipulator.common.items.manipulator;

import dev.wp.matter_manipulator.client.ClientEvents;
import dev.wp.matter_manipulator.client.gui.RadialMenuBuilder;
import dev.wp.matter_manipulator.client.gui.RadialMenuScreen;
import dev.wp.matter_manipulator.client.gui.TransformScreen;
import dev.wp.matter_manipulator.client.render.MMRenderer;
import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.building.PendingBuild;
import dev.wp.matter_manipulator.common.config.MMModConfig;
import dev.wp.matter_manipulator.common.networking.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ItemMatterManipulator extends Item {

    private final MMTier tier;

    public ItemMatterManipulator(MMTier tier) {
        super(new Item.Properties().stacksTo(1));
        this.tier = tier;
    }

    public MMTier getTier() {
        return tier;
    }

    // ── Interaction ──────────────────────────────────────────────────────────────

    @Override
    public @Nonnull InteractionResult useOn(UseOnContext ctx) {
        var player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        var level = ctx.getLevel();
        var stack = ctx.getItemInHand();
        var pos = ctx.getClickedPos();
        var face = ctx.getClickedFace();

        if (level.isClientSide) {
            var cfg = MMState.of(stack).getConfig();
            if (cfg.locked) return InteractionResult.SUCCESS;

            // Block-selection actions target the actual clicked block; area-corner selection uses the adjacent face
            var selectedPos = (cfg.action != null && cfg.action.isDirectBlockPick())
                    ? pos
                    : (player.isShiftKeyDown() ? pos : pos.relative(face));

            // In COPYING/MOVING mode, right-click sets Coord C (Paste) by default, NOT Coord A (Source).
            // Source coordinates (A/B) must be set via the Mark buttons (actions).
            int which = (cfg.placeMode == PlaceMode.COPYING || cfg.placeMode == PlaceMode.MOVING) ? 2 : 0;

            MMNetwork.CHANNEL.sendToServer(
                    new PacketSetCoord(which, new Location(level.dimension(), selectedPos)));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }


    /**
     * Right-click air → open the radial config menu, unless a block-selection action is active.
     */
    @Override
    public @Nonnull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            var cfg = MMState.of(stack).getConfig();
            if (cfg.action == null) {
                openRadialMenu(stack, player);
            } else if (!cfg.locked) {
                int which = (cfg.placeMode == PlaceMode.COPYING || cfg.placeMode == PlaceMode.MOVING) ? 2 : 0;
                MMNetwork.CHANNEL.sendToServer(new PacketSetCoord(which, new Location(level.dimension(), player.blockPosition())));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @OnlyIn(Dist.CLIENT)
    private void openRadialMenu(ItemStack stack, Player player) {
        var cfg = MMState.of(stack).getConfig();
        Minecraft.getInstance().setScreen(new RadialMenuScreen(buildRadialMenu(cfg, player)));
    }

    // spotless:off
    @OnlyIn(Dist.CLIENT)
    private RadialMenuBuilder buildRadialMenu(MMConfig cfg, Player player) {
        int allModes = MMCapability.ALLOW_GEOMETRY | MMCapability.ALLOW_COPYING
                | MMCapability.ALLOW_MOVING | MMCapability.ALLOW_EXCHANGING | MMCapability.ALLOW_CABLES;
        boolean hasMultiMode = Integer.bitCount(tier.capabilities & allModes) > 1;
        boolean hasRemove = tier.hasCapability(MMCapability.ALLOW_REMOVING);
        boolean hasCopy = tier.hasCapability(MMCapability.ALLOW_COPYING);
        boolean hasMove = tier.hasCapability(MMCapability.ALLOW_MOVING);
        boolean hasExch = tier.hasCapability(MMCapability.ALLOW_EXCHANGING);
        boolean hasCable = tier.hasCapability(MMCapability.ALLOW_CABLES);

        var b = new RadialMenuBuilder();

        // "Set Mode" branch — hidden for PROTO which only has GEOMETRY
        if (hasMultiMode) {
            b.branch().label(I18n.get("mm.gui.set_mode"))
                    .branch().label(I18n.get("mm.gui.set_remove_mode")).hidden(!hasRemove)
                    .option().label(I18n.get("mm.gui.remove_none")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetRemoveMode(RemoveMode.NONE))).done()
                    .option().label(I18n.get("mm.gui.remove_replaceable")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetRemoveMode(RemoveMode.REPLACEABLE))).done()
                    .option().label(I18n.get("mm.gui.remove_all")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetRemoveMode(RemoveMode.ALL))).done()
                    .done()
                    .option().label(I18n.get("mm.gui.geometry")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPlaceMode(PlaceMode.GEOMETRY))).done()
                    .option().label(I18n.get("mm.gui.copying")).hidden(!hasCopy).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPlaceMode(PlaceMode.COPYING))).done()
                    .option().label(I18n.get("mm.gui.moving")).hidden(!hasMove).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPlaceMode(PlaceMode.MOVING))).done()
                    .option().label(I18n.get("mm.gui.exchanging")).hidden(!hasExch).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPlaceMode(PlaceMode.EXCHANGING))).done()
                    .option().label(I18n.get("mm.gui.cables")).hidden(!hasCable).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPlaceMode(PlaceMode.CABLES))).done()
                    .done();
        } else if (hasRemove) {
            b.branch().label(I18n.get("mm.gui.set_remove_mode"))
                    .option().label(I18n.get("mm.gui.remove_none")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetRemoveMode(RemoveMode.NONE))).done()
                    .option().label(I18n.get("mm.gui.remove_replaceable")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetRemoveMode(RemoveMode.REPLACEABLE))).done()
                    .option().label(I18n.get("mm.gui.remove_all")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetRemoveMode(RemoveMode.ALL))).done()
                    .done();
        }

        // Mode-specific options
        switch (cfg.placeMode) {
            case GEOMETRY -> addGeometryOptions(b);
            case COPYING -> addCopyingOptions(b);
            case MOVING -> addMovingOptions(b);
            case EXCHANGING -> addExchangingOptions(b);
            case CABLES -> addCableOptions(b);
        }

        // Universal actions
        b.option().label(I18n.get("mm.gui.start_build")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketStartBuild())).done();
        b.option().label(I18n.get("mm.gui.cancel_build")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketCancelBuild())).done();

        return b;
    }

    @OnlyIn(Dist.CLIENT)
    private void addGeometryOptions(RadialMenuBuilder b) {
        b.branch().label(I18n.get("mm.gui.select_blocks"))
                .option().label(I18n.get("mm.gui.set_corners")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.GEOM_SELECTING_BLOCK, BlockSelectMode.CORNERS))).done()
                .option().label(I18n.get("mm.gui.set_edges")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.GEOM_SELECTING_BLOCK, BlockSelectMode.EDGES))).done()
                .option().label(I18n.get("mm.gui.set_faces")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.GEOM_SELECTING_BLOCK, BlockSelectMode.FACES))).done()
                .option().label(I18n.get("mm.gui.set_volumes")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.GEOM_SELECTING_BLOCK, BlockSelectMode.VOLUMES))).done()
                .option().label(I18n.get("mm.gui.set_all")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.GEOM_SELECTING_BLOCK, BlockSelectMode.ALL))).done()
                .option().label(I18n.get("mm.gui.clear_all")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketClearBlocks())).done()
                .done();
        b.branch().label(I18n.get("mm.gui.set_shape"))
                .option().label(I18n.get("mm.gui.line")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetShape(ShapeType.LINE))).done()
                .option().label(I18n.get("mm.gui.cube")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetShape(ShapeType.CUBE))).done()
                .option().label(I18n.get("mm.gui.sphere")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetShape(ShapeType.SPHERE))).done()
                .option().label(I18n.get("mm.gui.cylinder")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetShape(ShapeType.CYLINDER))).done()
                .done();
        addMoveCoordsOptions(b);
        addEditTransformOption(b);
    }

    @OnlyIn(Dist.CLIENT)
    private void addCopyingOptions(RadialMenuBuilder b) {
        b.option().label(I18n.get("mm.gui.mark_copy")).onClicked(() -> {
            if (MMModConfig.PASTE_AUTO_CLEAR.get()) {
                MMNetwork.CHANNEL.sendToServer(new PacketClearCoords());
                if (MMModConfig.RESET_TRANSFORM.get()) {
                    MMNetwork.CHANNEL.sendToServer(new PacketResetTransform());
                }
            }
            MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.MARK_COPY_A, null));
        }).done();

        b.branch().label(I18n.get("mm.gui.edit_stack"))
                .option().label(I18n.get("mm.gui.reset")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketResetArray())).done()
                .option().label(I18n.get("mm.gui.mark")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.MARK_ARRAY, null))).done()
                .done();

        b.branch().label(I18n.get("mm.gui.planning"))
                .option().label(I18n.get("mm.gui.plan_all_manual")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketRequestRequiredItems(PacketRequestRequiredItems.PLAN_ALL))).done()
                .option().label(I18n.get("mm.gui.plan_missing_manual")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketRequestRequiredItems(0))).done()
                .done();

        addEditTransformOption(b);

        b.option().label(I18n.get("mm.gui.mark_paste")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.MARK_PASTE_A, null))).done();
    }

    @OnlyIn(Dist.CLIENT)
    private void addMovingOptions(RadialMenuBuilder b) {
        b.option().label(I18n.get("mm.gui.mark_cut")).onClicked(() -> {
            if (MMModConfig.PASTE_AUTO_CLEAR.get()) {
                MMNetwork.CHANNEL.sendToServer(new PacketClearCoords());
                if (MMModConfig.RESET_TRANSFORM.get()) {
                    MMNetwork.CHANNEL.sendToServer(new PacketResetTransform());
                }
            }
            MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.MARK_CUT_A, null));
        }).done();
        addEditTransformOption(b);
        b.option().label(I18n.get("mm.gui.mark_paste")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.MARK_PASTE_A, null))).done();
    }

    @OnlyIn(Dist.CLIENT)
    private void addExchangingOptions(RadialMenuBuilder b) {
        b.branch().label(I18n.get("mm.gui.edit_replace_whitelist"))
                .option().label(I18n.get("mm.gui.clear")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketClearWhitelist())).done()
                .option().label(I18n.get("mm.gui.add_block")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.EXCH_ADD_REPLACE, null))).done()
                .option().label(I18n.get("mm.gui.set_block")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.EXCH_SET_REPLACE, null))).done()
                .done();
        b.option().label(I18n.get("mm.gui.set_block_to_replace_with")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.EXCH_SET_TARGET, null))).done();
        addMoveCoordsOptions(b);
        addEditTransformOption(b);
    }

    @OnlyIn(Dist.CLIENT)
    private void addCableOptions(RadialMenuBuilder b) {
        b.option().label(I18n.get("mm.gui.set_cable")).onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.PICK_CABLE, null))).done();
        addMoveCoordsOptions(b);
        addEditTransformOption(b);
    }

    @OnlyIn(Dist.CLIENT)
    private void addMoveCoordsOptions(RadialMenuBuilder b) {
        b.branch().label(I18n.get("mm.gui.move_coords"))
                .option().label(I18n.get("mm.gui.move_coord_a")).onClicked(() -> sendMoveCoords(PacketMoveCoords.Which.A)).done()
                .option().label(I18n.get("mm.gui.move_all")).onClicked(() -> sendMoveCoords(PacketMoveCoords.Which.ALL)).done()
                .option().label(I18n.get("mm.gui.move_coord_b")).onClicked(() -> sendMoveCoords(PacketMoveCoords.Which.B)).done()
                .option().label(I18n.get("mm.gui.move_here")).onClicked(() -> sendMoveCoords(PacketMoveCoords.Which.HERE)).done()
                .done();
    }

    @OnlyIn(Dist.CLIENT)
    private static void sendMoveCoords(PacketMoveCoords.Which which) {
        var look = MMRenderer.getLookingAt(Minecraft.getInstance(), null);
        if (look == null) return;
        MMNetwork.CHANNEL.sendToServer(new PacketMoveCoords(which, look));
    }

    /** Opens the transform screen, deferred so the radial menu teardown can't wipe it. */
    @OnlyIn(Dist.CLIENT)
    private void addEditTransformOption(RadialMenuBuilder b) {
        b.option().label(I18n.get("mm.gui.edit_transform"))
                .onClicked(() -> ClientEvents.openDeferred(new TransformScreen()))
                .done();
    }
    // spotless:on

    // ── Tick (building loop) ─────────────────────────────────────────────────────

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        if (!PendingBuild.hasActiveBuild(player.getUUID())) return;

        var state = MMState.of(stack);
        PendingBuild.tick(player, stack, tier, state);
        state.saveIfDirty();
    }

    // ── Tooltip ──────────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        var state = MMState.of(stack);
        long charge = state.getCharge();
        long max = tier.maxEnergy;
        tooltip.add(Component.literal(String.format("Energy: %,d / %,d FE", charge, max)));
        tooltip.add(Component.literal("Range: " + (tier.range < 0 ? "Unlimited" : tier.range + " blocks")));
        tooltip.add(Component.literal("Speed: " + tier.placeSpeed + " blocks/tick"));

        var cfg = state.getConfig();
        if (cfg.coordA != null) tooltip.add(Component.literal("A: " + cfg.coordA.pos.toShortString()));
        if (cfg.coordB != null) tooltip.add(Component.literal("B: " + cfg.coordB.pos.toShortString()));
        if (cfg.coordC != null) tooltip.add(Component.literal("C: " + cfg.coordC.pos.toShortString()));

        tooltip.add(Component.literal("Mode: " + cfg.placeMode + " | Shape: " + cfg.shape));
        if (cfg.locked) {
            tooltip.add(Component.literal("POSITIONS LOCKED").withStyle(ChatFormatting.RED));
        }
    }

    // ── Forge Energy Capability ──────────────────────────────────────────────────

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ICapabilityProvider() {
            private final LazyOptional<IEnergyStorage> energyCap =
                    LazyOptional.of(() -> new StackEnergyStorage(stack, tier));

            @Override
            public @Nonnull <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
                if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
                return LazyOptional.empty();
            }
        };
    }

    // ── Energy Storage backed by NBT ─────────────────────────────────────────────

    private static class StackEnergyStorage implements IEnergyStorage {

        private final ItemStack stack;
        private final MMTier tier;

        StackEnergyStorage(ItemStack stack, MMTier tier) {
            this.stack = stack;
            this.tier = tier;
        }

        private MMState state() {
            return MMState.of(stack);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            var s = state();
            long current = s.getCharge();
            long space = tier.maxEnergy - current;
            long accepted = Math.min(space, maxReceive);
            if (!simulate) s.setCharge(current + accepted);
            return (int) accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            var s = state();
            long current = s.getCharge();
            long extracted = Math.min(current, maxExtract);
            if (!simulate) s.setCharge(current - extracted);
            return (int) extracted;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(state().getCharge(), Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(tier.maxEnergy, Integer.MAX_VALUE);
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
