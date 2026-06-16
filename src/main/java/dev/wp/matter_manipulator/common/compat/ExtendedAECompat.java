package dev.wp.matter_manipulator.common.compat;

import dev.wp.matter_manipulator.common.building.analysis.BlockApplyContext;
import dev.wp.matter_manipulator.common.building.analysis.ITileAnalysis;
import dev.wp.matter_manipulator.common.building.analysis.RequiredItems;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.glodblock.github.extendedae.common.EPPItemAndBlock;
import com.glodblock.github.extendedae.common.me.wireless.WirelessConnect;
import com.glodblock.github.extendedae.common.tileentities.TileWirelessConnector;
import com.glodblock.github.extendedae.common.tileentities.TileWirelessHub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * ExtendedAE ({@code expatternprovider}) soft-dependency compat: reproduces wireless-connector links
 * after a copy. This class imports ExtendedAE types, so callers must guard on {@code Mods.EXTENDED_AE}.
 *
 * <p>ExtendedAE links are frequency-based and global per level — two endpoints that share a frequency
 * pair up automatically. So a copied connector pair must get a FRESH shared frequency on paste (so it
 * links to its own copy, not the still-existing source pair), while a connector linked to a block
 * outside the copy keeps its original frequency (to link to that external block) — and only when the
 * player opted in via {@code linkExternalHubs}. The original frequency is the natural pair key: both
 * endpoints of a link share it, so remapping it consistently within a build re-pairs the copies.
 */
public class ExtendedAECompat {
    public static boolean isWireless(@Nullable BlockEntity be) {
        return be instanceof TileWirelessConnector || be instanceof TileWirelessHub;
    }

    /**
     * True if {@code be} is a wireless hub with no links to partners INSIDE the copied region. Such a
     * hub is pasted as a connector tethered to the original hub; the original keeps its other ports free,
     * so each subsequent copy/paste tethers to it too (external links — including those tether connectors
     * from earlier pastes — don't disqualify it). A hub that IS linked to a connector inside the region
     * is instead relocated as a hub, so its internal pair re-links via {@link #captureWireless}.
     */
    public static boolean isTetherableHub(@Nullable BlockEntity be, BlockPos regionMin, BlockPos regionMax) {
        if (!(be instanceof TileWirelessHub h)) return false;
        for (int port = 0; port < TileWirelessHub.MAX_PORT; port++) {
            if (h.getFrequency(port) == 0) continue;
            BlockPos partner = h.getOtherSide(port);
            if (partner != null && inRegion(partner, regionMin, regionMax)) return false; // linked inside the copy
        }
        return true;
    }

    /** The wireless connector block's default state, for the empty-hub→connector smart-copy swap. */
    public static BlockState connectorState() {
        return EPPItemAndBlock.WIRELESS_CONNECTOR.defaultBlockState();
    }

    /** A smart-copy analysis that tethers a pasted connector to a free port on the original hub. */
    public static ITileAnalysis tetherToHub(BlockPos hub) {
        return new TetherAnalysis(hub);
    }

    /** One captured wireless link: a frequency on a port (-1 = single connector), internal or external. */
    private record Link(long freq, int port, boolean external) {}

    /**
     * Captures the frequency + internal/external classification of every linked port on a copied
     * connector or hub, or null if none are worth restoring. A link is "internal" when its partner sits
     * inside the copied region (both ends copied); external links are kept only when {@code linkExternalHubs}.
     */
    @Nullable
    public static WirelessAnalysis captureWireless(
            BlockEntity be, BlockPos regionMin, BlockPos regionMax, boolean linkExternalHubs) {
        List<Link> links = new ArrayList<>();
        if (be instanceof TileWirelessConnector c) {
            addLink(links, c.getFrequency(), -1, c.getOtherSide(), regionMin, regionMax, linkExternalHubs);
        } else if (be instanceof TileWirelessHub h) {
            for (int port = 0; port < TileWirelessHub.MAX_PORT; port++) {
                addLink(links, h.getFrequency(port), port, h.getOtherSide(port), regionMin, regionMax, linkExternalHubs);
            }
        }
        if (links.isEmpty()) return null;
        WirelessAnalysis a = new WirelessAnalysis();
        a.links = links;
        return a;
    }

    private static void addLink(
            List<Link> links,
            long freq,
            int port,
            @Nullable BlockPos partner,
            BlockPos regionMin,
            BlockPos regionMax,
            boolean linkExternalHubs) {
        if (freq == 0) return; // unlinked port
        boolean internal = partner != null && inRegion(partner, regionMin, regionMax);
        if (!internal && !linkExternalHubs) return; // external link the player didn't opt to keep
        links.add(new Link(freq, port, !internal));
    }

    private static boolean inRegion(BlockPos p, BlockPos min, BlockPos max) {
        return p.getX() >= min.getX()
                && p.getX() <= max.getX()
                && p.getY() >= min.getY()
                && p.getY() <= max.getY()
                && p.getZ() >= min.getZ()
                && p.getZ() <= max.getZ();
    }

    /**
     * Re-links a pasted connector/hub. Internal links are remapped through the build-scoped frequency
     * map (so both copies of a pair share one fresh frequency and link to each other); external links
     * keep their original frequency to link to the still-present external endpoint. The connector's own
     * {@code serverTick} establishes the link once the frequency is set.
     */
    public static class WirelessAnalysis implements ITileAnalysis {
        private List<Link> links;

        @Override
        public boolean apply(BlockApplyContext ctx) {
            BlockEntity be = ctx.getBlockEntity();
            Map<Long, Long> remap = ctx.wirelessRemap();
            for (Link link : links) {
                long target = link.external()
                        ? link.freq()
                        : (remap != null
                                ? remap.computeIfAbsent(link.freq(), k -> WirelessConnect.G.genFreq())
                                : WirelessConnect.G.genFreq());
                if (be instanceof TileWirelessConnector c) {
                    c.setFrequency(target);
                } else if (be instanceof TileWirelessHub h) {
                    h.setFrequency(target, link.port());
                }
            }
            if (be != null) be.setChanged();
            return true;
        }

        @Override
        public void collectRequiredItems(RequiredItems out) {} // the connector block itself is the charged item

        @Override
        public void transform(MMTransform transform) {} // frequencies carry no orientation

        @Override
        public ITileAnalysis copy() {
            WirelessAnalysis dup = new WirelessAnalysis();
            dup.links = new ArrayList<>(links);
            return dup;
        }
    }

    /**
     * Links a pasted wireless connector to a free port on the original (source) hub with a fresh
     * frequency — the empty-hub→connector smart-copy. A free port is found by frequency (not the hub's
     * {@code allocatePort}, whose live-connection check wouldn't see ports assigned earlier this tick),
     * so several copies tether to distinct ports. If the source hub is full the connector is left
     * unlinked. The source hub stays in place (COPY), so its ports fill as copies are placed.
     */
    public static class TetherAnalysis implements ITileAnalysis {
        private final BlockPos hub;

        public TetherAnalysis(BlockPos hub) {
            this.hub = hub;
        }

        @Override
        public boolean apply(BlockApplyContext ctx) {
            if (!(ctx.getBlockEntity() instanceof TileWirelessConnector connector)) return true;
            if (!(ctx.level().getBlockEntity(hub) instanceof TileWirelessHub h)) return true;
            int port = -1;
            for (int i = 0; i < TileWirelessHub.MAX_PORT; i++) {
                if (h.getFrequency(i) == 0) {
                    port = i;
                    break;
                }
            }
            if (port < 0) return true; // source hub full — leave the connector unlinked
            long freq = WirelessConnect.G.genFreq();
            connector.setFrequency(freq);
            h.setFrequency(freq, port);
            h.setChanged();
            connector.setChanged();
            return true;
        }

        @Override
        public void collectRequiredItems(RequiredItems out) {}

        @Override
        public void transform(MMTransform transform) {}

        @Override
        public ITileAnalysis copy() {
            return new TetherAnalysis(hub);
        }
    }
}
