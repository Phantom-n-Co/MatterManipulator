package dev.wp.matter_manipulator.common.items.manipulator;

import dev.wp.matter_manipulator.client.ClientEvents;
import dev.wp.matter_manipulator.client.gui.RadialMenuBuilder;
import dev.wp.matter_manipulator.client.gui.RadialMenuScreen;
import dev.wp.matter_manipulator.client.gui.TransformScreen;
import dev.wp.matter_manipulator.client.render.MMRenderer;
import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.building.PendingBuild;
import dev.wp.matter_manipulator.common.compat.GTCompat;
import dev.wp.matter_manipulator.common.config.MMModConfig;
import dev.wp.matter_manipulator.common.networking.MMNetwork;
import dev.wp.matter_manipulator.common.networking.PacketConfigAction;
import dev.wp.matter_manipulator.common.networking.PacketMoveCoords;
import dev.wp.matter_manipulator.common.networking.PacketRequestRequiredItems;
import dev.wp.matter_manipulator.common.networking.PacketSetCoord;
import dev.wp.matter_manipulator.common.networking.PacketSetPendingAction;
import dev.wp.matter_manipulator.common.networking.PacketSignal;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
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

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static dev.wp.matter_manipulator.MMMod.MODID;

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
            int which = cfg.placeMode.isCopyOrMove() ? 2 : 0;

            MMNetwork.CHANNEL.sendToServer(new PacketSetCoord(which, new Location(level.dimension(), selectedPos)));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Right-click air → sneak starts/cancels the build (it isn't targeting a block); otherwise open the
     * radial config menu, unless a block-selection action is active.
     */
    @Override
    public @Nonnull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // Sneak + right-click air (not aimed at a block) toggles the build, like the original.
            if (player.isShiftKeyDown()) {
                MMNetwork.CHANNEL.sendToServer(new PacketSignal(PacketSignal.Action.TOGGLE_BUILD));
                return InteractionResultHolder.sidedSuccess(stack, true);
            }
            var cfg = MMState.of(stack).getConfig();
            if (cfg.action == null) {
                openRadialMenu(stack, player);
            } else if (!cfg.locked) {
                int which = cfg.placeMode.isCopyOrMove() ? 2 : 0;
                BlockPos look = MMRenderer.getLookingAt(Minecraft.getInstance(), cfg.action);
                if (look == null) look = player.blockPosition();
                MMNetwork.CHANNEL.sendToServer(
                        new PacketSetCoord(which, new Location(level.dimension(), look)));
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
        int allModes = MMCapability.ALLOW_GEOMETRY
                | MMCapability.ALLOW_COPYING
                | MMCapability.ALLOW_MOVING
                | MMCapability.ALLOW_EXCHANGING
                | MMCapability.ALLOW_CABLES;
        boolean hasMultiMode = Integer.bitCount(tier.capabilities & allModes) > 1;
        boolean hasRemove = tier.hasCapability(MMCapability.ALLOW_REMOVING);
        boolean hasCopy = tier.hasCapability(MMCapability.ALLOW_COPYING);
        boolean hasMove = tier.hasCapability(MMCapability.ALLOW_MOVING);
        boolean hasExch = tier.hasCapability(MMCapability.ALLOW_EXCHANGING);
        boolean hasCable = tier.hasCapability(MMCapability.ALLOW_CABLES);

        var b = new RadialMenuBuilder();

        // "Set Mode" branch — hidden for PROTO which only has GEOMETRY
        if (hasMultiMode) {
            b.branch()
                    .label(I18n.get("gui." + MODID + "set_mode"))
                    .branch()
                    .label(I18n.get("gui." + MODID + "set_remove_mode"))
                    .hidden(!hasRemove)
                    .option()
                    .label(I18n.get("gui." + MODID + "remove_none"))
                    .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                            new PacketConfigAction(PacketConfigAction.Action.SET_REMOVE_MODE, RemoveMode.NONE.ordinal())))
                    .done()
                    .option()
                    .label(I18n.get("gui." + MODID + "remove_replaceable"))
                    .onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketConfigAction(
                            PacketConfigAction.Action.SET_REMOVE_MODE, RemoveMode.REPLACEABLE.ordinal())))
                    .done()
                    .option()
                    .label(I18n.get("gui." + MODID + "remove_all"))
                    .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                            new PacketConfigAction(PacketConfigAction.Action.SET_REMOVE_MODE, RemoveMode.ALL.ordinal())))
                    .done()
                    .done()
                    .option()
                    .label(I18n.get("gui." + MODID + "geometry"))
                    .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                            new PacketConfigAction(PacketConfigAction.Action.SET_PLACE_MODE, PlaceMode.GEOMETRY.ordinal())))
                    .done()
                    .option()
                    .label(I18n.get("gui." + MODID + "copying"))
                    .hidden(!hasCopy)
                    .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                            new PacketConfigAction(PacketConfigAction.Action.SET_PLACE_MODE, PlaceMode.COPYING.ordinal())))
                    .done()
                    .option()
                    .label(I18n.get("gui." + MODID + "moving"))
                    .hidden(!hasMove)
                    .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                            new PacketConfigAction(PacketConfigAction.Action.SET_PLACE_MODE, PlaceMode.MOVING.ordinal())))
                    .done()
                    .option()
                    .label(I18n.get("gui." + MODID + "exchanging"))
                    .hidden(!hasExch)
                    .onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketConfigAction(
                            PacketConfigAction.Action.SET_PLACE_MODE, PlaceMode.EXCHANGING.ordinal())))
                    .done()
                    .option()
                    .label(I18n.get("gui." + MODID + "cables"))
                    .hidden(!hasCable)
                    .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                            new PacketConfigAction(PacketConfigAction.Action.SET_PLACE_MODE, PlaceMode.CABLES.ordinal())))
                    .done()
                    .done();
        } else if (hasRemove) {
            b.branch()
                    .label(I18n.get("gui." + MODID + "set_remove_mode"))
                    .option()
                    .label(I18n.get("gui." + MODID + "remove_none"))
                    .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                            new PacketConfigAction(PacketConfigAction.Action.SET_REMOVE_MODE, RemoveMode.NONE.ordinal())))
                    .done()
                    .option()
                    .label(I18n.get("gui." + MODID + "remove_replaceable"))
                    .onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketConfigAction(
                            PacketConfigAction.Action.SET_REMOVE_MODE, RemoveMode.REPLACEABLE.ordinal())))
                    .done()
                    .option()
                    .label(I18n.get("gui." + MODID + "remove_all"))
                    .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                            new PacketConfigAction(PacketConfigAction.Action.SET_REMOVE_MODE, RemoveMode.ALL.ordinal())))
                    .done()
                    .done();
        }

        // Mode-specific options
        switch (cfg.placeMode) {
            case GEOMETRY -> addGeometryOptions(b);
            case COPYING -> addCopyingOptions(b, player);
            case MOVING -> addMovingOptions(b);
            case EXCHANGING -> addExchangingOptions(b);
            case CABLES -> addCableOptions(b);
        }

        // Build start/cancel is sneak-right-click (air) or the optional keybinds — no radial buttons.
        return b;
    }

    @OnlyIn(Dist.CLIENT)
    private void addGeometryOptions(RadialMenuBuilder b) {
        b.branch()
                .label(I18n.get("gui." + MODID + "select_blocks"))
                .option()
                .label(I18n.get("gui." + MODID + "set_corners"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketSetPendingAction(PendingAction.GEOM_SELECTING_BLOCK, SlotType.CORNER)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "set_edges"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketSetPendingAction(PendingAction.GEOM_SELECTING_BLOCK, SlotType.EDGE)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "set_faces"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketSetPendingAction(PendingAction.GEOM_SELECTING_BLOCK, SlotType.FACE)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "set_volumes"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketSetPendingAction(PendingAction.GEOM_SELECTING_BLOCK, SlotType.VOLUME)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "set_all"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketSetPendingAction(PendingAction.GEOM_SELECTING_BLOCK, SlotType.ALL)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "clear_all"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketConfigAction(PacketConfigAction.Action.CLEAR_BLOCKS)))
                .done()
                .done();
        b.branch()
                .label(I18n.get("gui." + MODID + "set_shape"))
                .option()
                .label(I18n.get("gui." + MODID + "line"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketConfigAction(PacketConfigAction.Action.SET_SHAPE, ShapeType.LINE.ordinal())))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "cube"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketConfigAction(PacketConfigAction.Action.SET_SHAPE, ShapeType.CUBE.ordinal())))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "sphere"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketConfigAction(PacketConfigAction.Action.SET_SHAPE, ShapeType.SPHERE.ordinal())))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "cylinder"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketConfigAction(PacketConfigAction.Action.SET_SHAPE, ShapeType.CYLINDER.ordinal())))
                .done()
                .done();
        addMoveCoordsOptions(b);
        addEditTransformOption(b);
    }

    @OnlyIn(Dist.CLIENT)
    private void addCopyingOptions(RadialMenuBuilder b, Player player) {
        // Live config read so the smart-copy toggle labels reflect their current state each frame.
        Supplier<MMConfig> live = () -> MMState.of(player.getMainHandItem()).getConfig();
        b.option()
                .label(I18n.get("gui." + MODID + "mark_copy"))
                .onClicked(() -> {
                    if (MMModConfig.PASTE_AUTO_CLEAR.get()) {
                        MMNetwork.CHANNEL.sendToServer(new PacketConfigAction(PacketConfigAction.Action.CLEAR_COORDS));
                        if (MMModConfig.RESET_TRANSFORM.get()) {
                            MMNetwork.CHANNEL.sendToServer(new PacketConfigAction(PacketConfigAction.Action.RESET_TRANSFORM));
                        }
                    }
                    MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.MARK_COPY_A, null));
                })
                .done();

        b.branch()
                .label(I18n.get("gui." + MODID + "edit_stack"))
                .option()
                .label(I18n.get("gui." + MODID + "reset"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketConfigAction(PacketConfigAction.Action.RESET_ARRAY)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "mark"))
                .onClicked(() ->
                        MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.MARK_ARRAY, null)))
                .done()
                .done();

        boolean hasAuto = Mods.AE2 && tier.hasCapability(MMCapability.CONNECTS_TO_AE);
        b.branch()
                .label(I18n.get("gui." + MODID + "planning"))
                .option()
                .label(I18n.get("gui." + MODID + "plan_all_manual"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketRequestRequiredItems(PacketRequestRequiredItems.PLAN_ALL)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "plan_missing_manual"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketRequestRequiredItems(0)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "create_plan_all"))
                .hidden(!hasAuto)
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketRequestRequiredItems(
                        PacketRequestRequiredItems.PLAN_ALL | PacketRequestRequiredItems.PLAN_PATTERN)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "create_plan_missing"))
                .hidden(!hasAuto)
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketRequestRequiredItems(PacketRequestRequiredItems.PLAN_PATTERN)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "autocraft_all"))
                .hidden(!hasAuto)
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketRequestRequiredItems(
                        PacketRequestRequiredItems.PLAN_ALL | PacketRequestRequiredItems.PLAN_AUTOCRAFT)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "autocraft_missing"))
                .hidden(!hasAuto)
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketRequestRequiredItems(PacketRequestRequiredItems.PLAN_AUTOCRAFT)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "clear_plan"))
                .hidden(!hasAuto)
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(new PacketSignal(PacketSignal.Action.CLEAR_PLAN)))
                .done()
                .done();

        addEditTransformOption(b);

        // Copy-exclusive smart-copy settings in their own sub-menu. Toggles keep the menu open and show
        // live [ON]/[OFF]; the sub-menu has a Back option. Hidden entirely when no smart-copy mod is present.
        b.branch()
                .label(I18n.get("gui." + MODID + "smart_copy"))
                .hidden(!Mods.GTCEU && !Mods.EXTENDED_AE)
                .backButton()
                .option()
                .label(() -> I18n.get("gui." + MODID + "replace_cribs") + (live.get().replaceCribsWithProxies ? " [ON]" : " [OFF]"))
                .hidden(!Mods.GTCEU)
                .keepOpen()
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketConfigAction(PacketConfigAction.Action.TOGGLE_REPLACE_CRIBS)))
                .done()
                .option()
                .label(() ->
                        I18n.get("gui." + MODID + "link_external_hubs") + (live.get().linkExternalHubs ? " [ON]" : " [OFF]"))
                .hidden(!Mods.EXTENDED_AE)
                .keepOpen()
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketConfigAction(PacketConfigAction.Action.TOGGLE_LINK_EXTERNAL_HUBS)))
                .done()
                .option()
                .label(() ->
                        I18n.get("gui." + MODID + "tether_empty_hubs") + (live.get().tetherEmptyHubs ? " [ON]" : " [OFF]"))
                .hidden(!Mods.EXTENDED_AE)
                .keepOpen()
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketConfigAction(PacketConfigAction.Action.TOGGLE_TETHER_EMPTY_HUBS)))
                .done()
                .done();

        b.option()
                .label(I18n.get("gui." + MODID + "mark_paste"))
                .onClicked(() ->
                        MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.MARK_PASTE_A, null)))
                .done();
    }

    @OnlyIn(Dist.CLIENT)
    private void addMovingOptions(RadialMenuBuilder b) {
        b.option()
                .label(I18n.get("gui." + MODID + "mark_cut"))
                .onClicked(() -> {
                    if (MMModConfig.PASTE_AUTO_CLEAR.get()) {
                        MMNetwork.CHANNEL.sendToServer(new PacketConfigAction(PacketConfigAction.Action.CLEAR_COORDS));
                        if (MMModConfig.RESET_TRANSFORM.get()) {
                            MMNetwork.CHANNEL.sendToServer(new PacketConfigAction(PacketConfigAction.Action.RESET_TRANSFORM));
                        }
                    }
                    MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.MARK_CUT_A, null));
                })
                .done();
        addEditTransformOption(b);
        b.option()
                .label(I18n.get("gui." + MODID + "mark_paste"))
                .onClicked(() ->
                        MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.MARK_PASTE_A, null)))
                .done();
    }

    @OnlyIn(Dist.CLIENT)
    private void addExchangingOptions(RadialMenuBuilder b) {
        b.branch()
                .label(I18n.get("gui." + MODID + "edit_replace_whitelist"))
                .option()
                .label(I18n.get("gui." + MODID + "clear_replace_whitelist"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketConfigAction(PacketConfigAction.Action.CLEAR_WHITELIST)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "add_block"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketSetPendingAction(PendingAction.EXCH_ADD_REPLACE, null)))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "set_block"))
                .onClicked(() -> MMNetwork.CHANNEL.sendToServer(
                        new PacketSetPendingAction(PendingAction.EXCH_SET_REPLACE, null)))
                .done()
                .done();
        b.option()
                .label(I18n.get("gui." + MODID + "set_block_to_replace_with"))
                .onClicked(() ->
                        MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.EXCH_SET_TARGET, null)))
                .done();
        addMoveCoordsOptions(b);
        addEditTransformOption(b);
    }

    @OnlyIn(Dist.CLIENT)
    private void addCableOptions(RadialMenuBuilder b) {
        b.option()
                .label(I18n.get("gui." + MODID + "set_cable"))
                .onClicked(() ->
                        MMNetwork.CHANNEL.sendToServer(new PacketSetPendingAction(PendingAction.PICK_CABLE, null)))
                .done();
        addMoveCoordsOptions(b);
        addEditTransformOption(b);
    }

    @OnlyIn(Dist.CLIENT)
    private void addMoveCoordsOptions(RadialMenuBuilder b) {
        b.branch()
                .label(I18n.get("gui." + MODID + "move_coords"))
                .option()
                .label(I18n.get("gui." + MODID + "move_coord_a"))
                .onClicked(() -> sendMoveCoords(PacketMoveCoords.Which.A))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "move_all"))
                .onClicked(() -> sendMoveCoords(PacketMoveCoords.Which.ALL))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "move_coord_b"))
                .onClicked(() -> sendMoveCoords(PacketMoveCoords.Which.B))
                .done()
                .option()
                .label(I18n.get("gui." + MODID + "move_here"))
                .onClicked(() -> sendMoveCoords(PacketMoveCoords.Which.HERE))
                .done()
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
        b.option()
                .label(I18n.get("gui." + MODID + "edit_transform"))
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
        long max = tier.effectiveMaxEnergy();
        String unit = MMTier.usesEU() ? "EU" : "FE";
        tooltip.add(Component.literal(String.format("Energy: %,d / %,d %s", charge, max, unit)));
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
        // With GregTech present the manipulator is a GT electric item (EU, up to Long.MAX_VALUE);
        // otherwise it exposes Forge Energy (FE), which is int-capped.
        if (Mods.GTCEU) {
            return GTCompat.createElectricItemProvider(stack, tier);
        }
        return new ICapabilityProvider() {
            private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> new StackEnergyStorage(stack, tier));

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
            long space = tier.effectiveMaxEnergy() - current;
            long accepted = Math.min(space, maxReceive);
            if (accepted < 0) accepted = 0;
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
            return (int) Math.min(tier.effectiveMaxEnergy(), Integer.MAX_VALUE);
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
