package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.building.BuildGenerator;
import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.building.analysis.RequiredItems;
import dev.wp.matter_manipulator.common.compat.AECompat;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;
import dev.wp.matter_manipulator.common.utils.MMUtils;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static dev.wp.matter_manipulator.MMMod.MODID;

/** Radial menu "Planning" options: computes the material list for the current copy and prints it to chat. */
public class PacketRequestRequiredItems {
    /** Include all blocks rather than only those that don't match the world yet. */
    public static final int PLAN_ALL = 0b1;
    /** Register a terminal-requestable plan pattern on the bound AE2 uplink. */
    public static final int PLAN_PATTERN = 0b10;
    /** Have the uplink itself auto-craft the plan once (RP-style auto plan). */
    public static final int PLAN_AUTOCRAFT = 0b100;

    public final int flags;

    public PacketRequestRequiredItems(int flags) {
        this.flags = flags;
    }

    public static void encode(PacketRequestRequiredItems pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.flags);
    }

    public static PacketRequestRequiredItems decode(FriendlyByteBuf buf) {
        return new PacketRequestRequiredItems(buf.readVarInt());
    }

    public static void handle(PacketRequestRequiredItems pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var state = MMState.ofHeld(player.getMainHandItem());
            if (state == null) return;

            var cfg = state.getConfig();
            if (cfg.placeMode != PlaceMode.COPYING) return;

            boolean planAll = (pkt.flags & PLAN_ALL) != 0;

            if (cfg.coordA == null || cfg.coordB == null || !cfg.coordA.dimension.equals(cfg.coordB.dimension)) {
                MMUtils.sendErrorToPlayer(player, MODID + "info.error.must_have_copy_region");
                return;
            }

            var eff = cfg.copy();
            if (eff.coordC == null) {
                if (planAll) {
                    eff.coordC = eff.coordA;
                } else {
                    MMUtils.sendErrorToPlayer(player, MODID + "info.error.must_have_paste_region");
                    return;
                }
            }

            var level = player.serverLevel();
            var blocks = BuildGenerator.generateBlocks(eff, level);

            boolean planPattern = (pkt.flags & PLAN_PATTERN) != 0;

            RequiredItems req = new RequiredItems();
            for (var pb : blocks) {
                if (!planAll) {
                    var pos = eff.coordC.pos.offset(pb.relPos);
                    if (level.isLoaded(pos) && level.getBlockState(pos) == pb.spec.state) continue;
                }
                // The block item itself (empty for itemless hosts like an AE2 cable bus) plus whatever
                // the attached analyses (covers, AE parts/cable, container contents, fluids) install.
                req.add(pb.spec.toStack());
                pb.collectRequiredItems(req);
            }
            Map<Item, Long> required = req.items;
            Map<Fluid, Long> requiredFluids = req.fluids;

            if (required.isEmpty() && requiredFluids.isEmpty()) {
                MMUtils.sendInfoToPlayer(player, MODID + "info.none");
                return;
            }

            boolean bound = Mods.AE2 && cfg.uplink != null && AECompat.hasUplink(level, cfg.uplink);
            var inv = new MMInventory(player, cfg.uplink);

            MMUtils.sendInfoToPlayer(player, MODID + "info.required_items");
            for (var entry : required.entrySet()) {
                long count = entry.getValue();
                ItemStack itemStack = new ItemStack(entry.getKey());
                long available = inv.countAvailable(itemStack);
                long missing = Math.max(0, count - available);

                var line = Component.literal("  ")
                        .append(entry.getKey().getDescription())
                        .append(Component.literal(": " + count).withStyle(ChatFormatting.GOLD));
                if (missing > 0) {
                    line.append(Component.literal(" (" + missing + " missing)").withStyle(ChatFormatting.RED));
                }
                MMUtils.sendInfoToPlayer(player, line);
            }

            if (!requiredFluids.isEmpty()) {
                MMUtils.sendInfoToPlayer(player, MODID + "info.required_fluids");
                for (var entry : requiredFluids.entrySet()) {
                    long mb = entry.getValue();
                    FluidStack fluidStack = new FluidStack(entry.getKey(), 1);
                    long available = bound ? AECompat.uplinkFluidAvailable(level, cfg.uplink, fluidStack) : 0;
                    long missing = Math.max(0, mb - available);

                    var line = Component.literal("  ")
                            .append(fluidStack.getDisplayName())
                            .append(Component.literal(": " + mb + " mB").withStyle(ChatFormatting.GOLD));
                    if (missing > 0) {
                        line.append(Component.literal(" (" + missing + " mB missing)")
                                .withStyle(ChatFormatting.RED));
                    }
                    MMUtils.sendInfoToPlayer(player, line);
                }
            }

            boolean autocraft = (pkt.flags & PLAN_AUTOCRAFT) != 0;
            if (planPattern || autocraft) {
                if (!bound) {
                    MMUtils.sendErrorToPlayer(player, MODID + "info.error.no_uplink");
                } else {
                    List<ItemStack> planItems = new ArrayList<>(required.size());
                    for (var entry : required.entrySet()) {
                        planItems.add(
                                new ItemStack(entry.getKey(), (int) Math.min(entry.getValue(), Integer.MAX_VALUE)));
                    }
                    boolean ok = AECompat.uplinkAddPlan(level, cfg.uplink, player.getUUID(), planItems, autocraft);
                    MMUtils.sendInfoToPlayer(
                            player,
                            ok
                                    ? (autocraft ? MODID + "info.autocraft_started" : MODID + "info.plan_created")
                                    : MODID + "info.plan_full");
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
