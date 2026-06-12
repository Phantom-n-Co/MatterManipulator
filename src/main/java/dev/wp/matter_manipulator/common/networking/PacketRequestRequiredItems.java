package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.building.BuildGenerator;
import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;
import dev.wp.matter_manipulator.common.utils.MMUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Radial menu "Planning" options: computes the material list for the current copy and prints it to chat. */
public class PacketRequestRequiredItems {

    /** Include all blocks rather than only those that don't match the world yet. */
    public static final int PLAN_ALL = 0b1;

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

            if (cfg.coordA == null || cfg.coordB == null
                    || !cfg.coordA.dimension.equals(cfg.coordB.dimension)) {
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

            Map<Item, Long> required = new LinkedHashMap<>();
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
            }

            if (required.isEmpty()) {
                MMUtils.sendInfoToPlayer(player, "mm.info.none");
                return;
            }

            var inv = new MMInventory(player);
            MMUtils.sendInfoToPlayer(player, "mm.info.required_items");
            for (var entry : required.entrySet()) {
                long count = entry.getValue();
                long available = inv.countInInventory(new ItemStack(entry.getKey()));
                long missing = Math.max(0, count - available);

                var line = Component.literal("  ")
                        .append(entry.getKey().getDescription())
                        .append(Component.literal(": " + count).withStyle(ChatFormatting.GOLD));
                if (missing > 0) {
                    line.append(Component.literal(" (" + missing + " missing)").withStyle(ChatFormatting.RED));
                }
                MMUtils.sendInfoToPlayer(player, line);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Pasting only fills containers with items the player can supply, so plans must
     * count stored contents as required items too.
     */
    private static void mergeStoredItems(Map<Item, Long> required, CompoundTag tileData) {
        if (tileData.contains("Items", Tag.TAG_LIST)) {
            ListTag items = tileData.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = ItemStack.of(items.getCompound(i));
                if (!stack.isEmpty()) {
                    required.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
                }
            }
        }
        for (String key : new String[]{"Book", "RecordItem"}) {
            if (tileData.contains(key, Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.of(tileData.getCompound(key));
                if (!stack.isEmpty()) {
                    required.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
                }
            }
        }
    }
}
