package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.building.BuildGenerator;
import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.compat.AECompat;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;
import dev.wp.matter_manipulator.common.utils.MMUtils;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
                MMUtils.sendErrorToPlayer(player, "mm.info.error.must_have_copy_region");
                return;
            }

            var eff = cfg.copy();
            if (eff.coordC == null) {
                if (planAll) {
                    eff.coordC = eff.coordA;
                } else {
                    MMUtils.sendErrorToPlayer(player, "mm.info.error.must_have_paste_region");
                    return;
                }
            }

            var level = player.serverLevel();
            var blocks = BuildGenerator.generateBlocks(eff, level);

            boolean planPattern = (pkt.flags & PLAN_PATTERN) != 0;

            Map<Item, Long> required = new LinkedHashMap<>();
            Map<Fluid, Long> requiredFluids = new LinkedHashMap<>();
            for (var pb : blocks) {
                var stack = pb.spec.toStack();
                if (stack.isEmpty()) continue;
                if (!planAll) {
                    var pos = eff.coordC.pos.offset(pb.relPos);
                    if (level.isLoaded(pos) && level.getBlockState(pos) == pb.spec.state) continue;
                }
                required.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
                if (pb.spec.contents != null) {
                    // Captured through the source's item handler — works for any container
                    for (int i = 0; i < pb.spec.contents.size(); i++) {
                        ItemStack stored = ItemStack.of(pb.spec.contents.getCompound(i));
                        if (!stored.isEmpty()) {
                            required.merge(stored.getItem(), (long) stored.getCount(), Long::sum);
                        }
                    }
                } else if (pb.spec.tileData != null) {
                    mergeStoredItems(required, pb.spec.tileData);
                }
                if (pb.spec.extraItems != null) {
                    for (ItemStack extra : pb.spec.extraItems) {
                        if (!extra.isEmpty()) {
                            required.merge(extra.getItem(), (long) extra.getCount(), Long::sum);
                        }
                    }
                }
                if (pb.spec.fluids != null) {
                    for (int i = 0; i < pb.spec.fluids.size(); i++) {
                        FluidStack fs = FluidStack.loadFluidStackFromNBT(pb.spec.fluids.getCompound(i));
                        if (!fs.isEmpty()) {
                            requiredFluids.merge(fs.getFluid(), (long) fs.getAmount(), Long::sum);
                        }
                    }
                }
            }

            if (required.isEmpty() && requiredFluids.isEmpty()) {
                MMUtils.sendInfoToPlayer(player, "mm.info.none");
                return;
            }

            boolean bound = Mods.AE2 && cfg.uplink != null && AECompat.hasUplink(level, cfg.uplink);
            var inv = new MMInventory(player, cfg.uplink);

            MMUtils.sendInfoToPlayer(player, "mm.info.required_items");
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
                MMUtils.sendInfoToPlayer(player, "mm.info.required_fluids");
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
                    MMUtils.sendErrorToPlayer(player, "mm.info.error.no_uplink");
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
                                    ? (autocraft ? "mm.info.autocraft_started" : "mm.info.plan_created")
                                    : "mm.info.plan_full");
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Counts the single-slot items a copy keeps that have no item-handler capability — lectern
     * books and jukebox records. Container inventories are counted from the {@code contents}
     * snapshot instead (which covers any item-handler layout), so {@code Items} is intentionally
     * not scanned here: it would over-count blocks whose contents the whitelist won't paste.
     */
    private static void mergeStoredItems(Map<Item, Long> required, CompoundTag tileData) {
        for (String key : new String[] { "Book", "RecordItem" }) {
            if (tileData.contains(key, Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.of(tileData.getCompound(key));
                if (!stack.isEmpty()) {
                    required.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
                }
            }
        }
    }
}
